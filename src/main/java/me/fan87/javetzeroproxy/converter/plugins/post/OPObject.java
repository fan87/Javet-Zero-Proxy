package me.fan87.javetzeroproxy.converter.plugins.post;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.V8Scope;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueObject;
import com.caoccao.javet.values.reference.builtin.V8ValueBuiltInObject;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;
import me.fan87.javetzeroproxy.converter.AbstractV8ObjectPlugin;

import static me.fan87.javetzeroproxy.converter.prototype.JSClassPrototypeMaker.PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE;

public class OPObject extends AbstractV8ObjectPlugin {

    @Override
    public V8Value apply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object, Aborter aborter) throws JavetException {
        aborter.abort();
        output.close();
        V8Value originalBinding = converter.getBindingOf(object);
        if (originalBinding != null) return originalBinding;

        V8ValueObject v8Value;
        Class<?> objectClass = object.getClass();

        try (V8Scope v8Scope = runtime.getV8Scope()) {
            try(
                V8ValueObject prototype = converter.getPrototypeOf(objectClass, false);
                V8ValueObject v8ValueObject = v8Scope.createV8ValueObject()
            ) {
                V8ValueBuiltInObject v8ValueBuiltInObject = getBuiltinObject(runtime);
                v8Value = (V8ValueObject) v8ValueBuiltInObject.setPrototypeOf(v8ValueObject, prototype);
                converter.bindObject(v8Value, object);
            }
            v8Scope.setEscapable();
        }
        return v8Value;
    }

    @Override
    public Object tryRecoverOriginal(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value value) throws JavetException {
        return converter.getBoundObject(value);
    }

    @Override
    public boolean canRecover(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value value) throws JavetException {
        return value instanceof V8ValueObject && (((V8ValueObject) value)).hasPrivateProperty(PRIVATE_PROPERTY_NATIVE_OBJECT_HANDLE);
    }

    @Override
    public boolean canApply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object) {
        return true;
    }
}
