package me.fan87.javetzeroproxy.converter.plugins.post;

import com.caoccao.javet.enums.V8ProxyMode;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.V8Scope;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueObject;
import com.caoccao.javet.values.reference.builtin.V8ValueBuiltInObject;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;
import me.fan87.javetzeroproxy.converter.AbstractV8ObjectPlugin;
import me.fan87.javetzeroproxy.converter.prototype.CustomJavetProxyPrototypeStore;

import java.util.WeakHashMap;

import static me.fan87.javetzeroproxy.converter.prototype.JSClassPrototypeMaker.PRIVATE_PROPERTY_CLASS_WRAPPER_OBJECT;

public class OPReflectionClass extends AbstractV8ObjectPlugin {
    private final WeakHashMap<Class<?>, V8ValueObject> staticClassObjectsCache = new WeakHashMap<>();

    @Override
    public V8Value apply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object, Aborter aborter) throws JavetException {
        aborter.abort();
        output.close();
        V8Value originalBinding = converter.getBindingOf(object);
        if (originalBinding != null) return originalBinding;

        Class<?> clazz = (Class<?>) object;
        if (staticClassObjectsCache.containsKey(clazz)) {
            V8ValueObject v8Value = staticClassObjectsCache.get(clazz);
            return v8Value.toClone(true);
        }
        V8ValueObject v8Value;

        try (V8Scope v8Scope = runtime.getV8Scope()) {
            try(
                V8ValueObject prototype = converter.getPrototypeOf(Class.class, false);
                V8ValueObject v8ValueObject = runtime.createV8ValueObject()
            ) {
                V8ValueBuiltInObject v8ValueBuiltInObject = getBuiltinObject(runtime);
                v8Value = (V8ValueObject) v8ValueBuiltInObject.setPrototypeOf(v8ValueObject, prototype);
                long handle = converter.bindObject(v8Value, clazz);
                v8Value.setPrivateProperty(PRIVATE_PROPERTY_CLASS_WRAPPER_OBJECT, handle);
            }
            v8Scope.setEscapable();
        }
        V8ValueObject clone = v8Value.toClone(true);
        staticClassObjectsCache.put(clazz, clone);
        return v8Value;
    }

    @Override
    public Object tryRecoverOriginal(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value value) throws JavetException {
        if (value instanceof V8ValueObject) {
            V8ValueObject obj = (V8ValueObject) value;
            Long handle = obj.getPrivatePropertyLong(PRIVATE_PROPERTY_CLASS_WRAPPER_OBJECT);
            return converter.getBoundObject(runtime, handle);
        }
        return super.tryRecoverOriginal(converter, runtime, value);
    }

    @Override
    public boolean canRecover(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value value) throws JavetException {
        return value instanceof V8ValueObject && ((V8ValueObject) value).hasPrivateProperty(PRIVATE_PROPERTY_CLASS_WRAPPER_OBJECT);
    }

    @Override
    public boolean canApply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object) {
        return object instanceof Class<?>;
    }
}
