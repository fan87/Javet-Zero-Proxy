package me.fan87.javetzeroproxy.converter.plugins.post;

import com.caoccao.javet.enums.V8ProxyMode;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.V8Scope;
import com.caoccao.javet.interop.callback.IJavetDirectCallable;
import com.caoccao.javet.interop.callback.JavetCallbackContext;
import com.caoccao.javet.interop.callback.JavetCallbackType;
import com.caoccao.javet.utils.JavetResourceUtils;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueFunction;
import com.caoccao.javet.values.reference.V8ValueObject;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;
import me.fan87.javetzeroproxy.converter.wrapper.ClassWrapper;
import me.fan87.javetzeroproxy.converter.AbstractV8ObjectPlugin;
import me.fan87.javetzeroproxy.converter.prototype.CustomJavetProxyPrototypeStore;

import java.util.WeakHashMap;

public class OPClass extends AbstractV8ObjectPlugin {
    private final WeakHashMap<Class<?>, V8ValueFunction> staticClassObjectsCache = new WeakHashMap<>();

    @Override
    public V8Value apply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object, Aborter aborter) throws JavetException {
        aborter.abort();
        output.close();
        Class<?> clazz = ((ClassWrapper) object).wrapped;
        if (staticClassObjectsCache.containsKey(clazz)) {
            V8ValueObject v8Value = staticClassObjectsCache.get(clazz);
            return v8Value.toClone(true);
        }

        V8ValueFunction v8Value;
        try (V8Scope v8Scope = runtime.getV8Scope()) {
            V8ValueObject classPrototype = null;
            V8ValueObject objectPrototype = null;
            try {
                classPrototype = converter.getPrototypeOf(clazz, true);
                objectPrototype = converter.getPrototypeOf(clazz, false);
                v8Value = runtime.createV8ValueFunction(
                    new JavetCallbackContext("__new", JavetCallbackType.DirectCallThisAndResult,
                        (IJavetDirectCallable.ThisAndResult<Exception>) (thisObject, v8Values) -> {
                            V8ValueFunction function = ((V8ValueObject) thisObject).get("__javet__constructor");
                            return function.call(thisObject, v8Values);
                        }
                    )
                );
                v8Value.setPrototype(objectPrototype);
                getBuiltinObject(runtime).setPrototypeOf(v8Value, classPrototype);
            } finally {
                JavetResourceUtils.safeClose(classPrototype);
                JavetResourceUtils.safeClose(objectPrototype);
            }
            v8Scope.setEscapable();
        }
        V8ValueFunction clone = v8Value.toClone(true);
        staticClassObjectsCache.put(clazz, clone);
        return v8Value;
    }

    @Override
    public boolean canApply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object) {
        return object instanceof ClassWrapper && V8ProxyMode.isClassMode(((ClassWrapper) object).wrapped);
    }
}
