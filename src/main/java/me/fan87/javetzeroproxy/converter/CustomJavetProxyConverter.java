package me.fan87.javetzeroproxy.converter;

import com.caoccao.javet.annotations.CheckReturnValue;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.V8Scope;
import com.caoccao.javet.interop.converters.JavetObjectConverter;
import com.caoccao.javet.utils.JavetResourceUtils;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueObject;
import com.caoccao.javet.values.reference.V8ValueReference;
import me.fan87.javetzeroproxy.ScriptManager;
import me.fan87.javetzeroproxy.converter.plugins.post.*;
import me.fan87.javetzeroproxy.converter.plugins.pre.OPProxyPlugin;
import me.fan87.javetzeroproxy.converter.prototype.JSClassPrototypeMaker;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static me.fan87.javetzeroproxy.converter.prototype.JSClassPrototypeMaker.PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE;

@SuppressWarnings("unchecked")
public class CustomJavetProxyConverter extends JavetObjectConverter {
    private final GCHandler gcHandler;
    public final ScriptManager scriptManager;

    // Pre object plugins will be applied before super.toV8Value (Primitive conversion), such as map, set, array bridge, etc.
    private final List<AbstractV8ObjectPlugin> preObjectPlugin = new ArrayList<>();
    // Post object plugins will be applied after super.toV8Value, as a fallback option, such as class, function, and object
    // conversion
    private final List<AbstractV8ObjectPlugin> postObjectPlugin = Arrays.asList(
        new OPNonProxy(),
        new OPIJavetDirectProxyHandler(),
        new OPClass(),
        new OPFunction(),
        new OPReflectionClass(),
        new OPObject()
    );


    public CustomJavetProxyConverter(V8Runtime runtime, ScriptManager scriptManager) {
        super();
        this.scriptManager = scriptManager;

        this.gcHandler = new GCHandler(this, runtime);
        this.gcHandler.start();

        if (scriptManager.getProxyForBridgeSupport()) {
            preObjectPlugin.add(new OPProxyPlugin(this, runtime));
        }

        for (AbstractV8ObjectPlugin abstractV8ObjectPlugin : preObjectPlugin) {
            abstractV8ObjectPlugin.scriptManager = scriptManager;
        }
        for (AbstractV8ObjectPlugin abstractV8ObjectPlugin : postObjectPlugin) {
            abstractV8ObjectPlugin.scriptManager = scriptManager;
        }
    }

    public void close() {
        gcHandler.interrupt();
        objectBinding.clear();
    }


    private long currentHandle = Long.MIN_VALUE;

    private final Map<Long, Object> objectBinding = new HashMap<>();
    private final Map<Long, V8Value> bindings = new HashMap<>();

    public V8ValueObject getBindingOf(Object object) {
        if (!scriptManager.getUseSameBinding()) return null;
        long handle = System.identityHashCode(Objects.requireNonNull(object));
        try {
            V8Value v8Value = bindings.get(handle);
            if (v8Value == null) return null;
            if (v8Value.isClosed()) {
                gcHandler.deleteNow(handle);
                return null;
            }
            return v8Value.toClone(true);
        } catch (JavetException e) {
            throw new RuntimeException(e);
        }
    }
    public long bindObject(V8Value value, Object object) {
        try {
            V8ValueObject obj = (V8ValueObject) Objects.requireNonNull(value);
            long key = scriptManager.getUseSameBinding() ? System.identityHashCode(Objects.requireNonNull(object)) : currentHandle++;
            V8ValueReference actualOld = (V8ValueReference) bindings.get(key);
            Object old = objectBinding.put(key, object);

            if (scriptManager.getUseSameBinding()) {
                if (old != null) {
                    if (!actualOld.isClosed()) {
                        gcHandler.deleteNow(key);
                    } else {
                        throw new IllegalStateException("Object is already bound");
                    }
                }

                V8ValueObject clone = value.toClone(true);
                clone.setWeak();
                bindings.put(key, clone);
            }

            obj.setPrivateProperty(PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE, scriptManager.getV8Runtime().createV8ValueLong(key));
            gcHandler.add(key, obj);
            return key;
        } catch (JavetException e) {
            throw new RuntimeException(e);
        }
    }
    void deleteBinding(long binding) {
        objectBinding.remove(binding);
        if (scriptManager.getUseSameBinding()) {
            bindings.remove(binding);
        }
    }

    public Object getBoundObject(V8Value value) {
        try {
            if (value == null) return null;
            if (!(value instanceof V8ValueObject)) return null;
            Long privatePropertyLong = ((V8ValueObject) value).getPrivatePropertyLong(PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE);
            if (privatePropertyLong == null) return null;
            return objectBinding.get(privatePropertyLong);
        } catch (JavetException e) {
            throw new RuntimeException(e);
        }
    }
    public Object getBoundObject(V8Runtime runtime, long handle) {
        return objectBinding.get(handle);
    }

    @Override
    protected <T> T toObject(V8Value v8Value, int depth) throws JavetException {
        for (AbstractV8ObjectPlugin abstractV8ObjectPlugin : preObjectPlugin) {
            if (abstractV8ObjectPlugin.canRecover(this, v8Value.getV8Runtime(), v8Value)) {
                Object original = abstractV8ObjectPlugin.tryRecoverOriginal(this, v8Value.getV8Runtime(), v8Value);
                if (original != null) {
                    return (T) original;
                }
            }
        }
        for (AbstractV8ObjectPlugin abstractV8ObjectPlugin : postObjectPlugin) {
            if (abstractV8ObjectPlugin.canRecover(this, v8Value.getV8Runtime(), v8Value)) {
                Object original = abstractV8ObjectPlugin.tryRecoverOriginal(this, v8Value.getV8Runtime(), v8Value);
                if (original != null) {
                    return (T) original;
                }
            }
        }
        return super.toObject(v8Value, depth);
    }

    @Override
    @CheckReturnValue
    protected <T extends V8Value> T toV8Value(V8Runtime v8Runtime, Object object, final int depth) throws JavetException {

        // No conversion needed
        if (object instanceof V8Value) return ((T) object);
        if (object == null) return super.toV8Value(v8Runtime, null, depth);

        // Pre object plugin
        try (V8Scope scope = v8Runtime.getV8Scope()) {
            V8Value theValue = scope.createV8ValueObject();
            boolean applied = false;
            for (AbstractV8ObjectPlugin objectPlugin : preObjectPlugin) {
                if (objectPlugin.canApply(this, v8Runtime, theValue, object)) {
                    applied = true;
                    AtomicBoolean aborted = new AtomicBoolean(false);
                    theValue = objectPlugin.apply(this, v8Runtime, theValue, object, () -> {
                        aborted.set(true);
                    });
                    if (aborted.get()) {
                        scope.setEscapable(true);
                        return (T) theValue;
                    }
                }
            }
            if (applied) {
                scope.setEscapable(true);
                return (T) theValue;
            }
        }

        // We check if super is able to provide anything
        V8Value v8Value = super.toV8Value(v8Runtime, object, depth);
        if (v8Value != null && !(v8Value.isUndefined())) return (T) v8Value;

        // Post object plugin
        try (V8Scope scope = v8Runtime.getV8Scope()) {
            V8Value theValue = scope.createV8ValueObject();
            for (AbstractV8ObjectPlugin objectPlugin : postObjectPlugin) {
                if (objectPlugin.canApply(this, v8Runtime, theValue, object)) {
                    AtomicBoolean aborted = new AtomicBoolean(false);
                    theValue = objectPlugin.apply(this, v8Runtime, theValue, object, () -> {
                        aborted.set(true);
                    });
                    if (aborted.get()) {
                        scope.setEscapable(true);
                        return (T) theValue;
                    }
                }
            }
            scope.setEscapable(true);
            return (T) theValue;
        }
    }

    private final Map<Class<?>, V8ValueObject> staticPrototypes = new WeakHashMap<>();
    private final Map<Class<?>, V8ValueObject> nonStaticPrototypes = new WeakHashMap<>();

    public V8ValueObject getPrototypeOf(Class<?> clazz, boolean staticMode) throws JavetException {
        V8Runtime v8Runtime = scriptManager.getV8Runtime();
        Map<Class<?>, V8ValueObject> cacheMap = staticMode ? staticPrototypes : nonStaticPrototypes;
        if (cacheMap.containsKey(clazz)) {
            return cacheMap.get(clazz).toClone(true);
        }
        try (V8Scope v8Scope = v8Runtime.getV8Scope()) {
            V8ValueObject v8ValueObject = v8Scope.createV8ValueObject();
            V8ValueObject output;
            new JSClassPrototypeMaker<>(this, v8Runtime, v8ValueObject, clazz, staticMode).apply();
            if (clazz.getSuperclass() != null) {
                V8ValueObject superPrototype = getPrototypeOf(clazz.getSuperclass(), staticMode);
                output = (V8ValueObject) Objects.requireNonNull(scriptManager.getBuiltInObject())
                    .setPrototypeOf(v8ValueObject, superPrototype);
                JavetResourceUtils.safeClose(v8ValueObject);
            } else {
                output = v8ValueObject;
            }

            v8Scope.setEscapable(true);
            cacheMap.put(clazz, output.toClone(true));
            return output;
        }
    }

}
