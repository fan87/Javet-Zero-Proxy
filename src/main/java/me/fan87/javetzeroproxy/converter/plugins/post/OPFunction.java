package me.fan87.javetzeroproxy.converter.plugins.post;

import com.caoccao.javet.annotations.V8Convert;
import com.caoccao.javet.enums.V8ProxyMode;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.V8Scope;
import com.caoccao.javet.interop.callback.JavetCallbackContext;
import com.caoccao.javet.interop.proxy.*;
import com.caoccao.javet.utils.JavetResourceUtils;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.primitive.V8ValueLong;
import com.caoccao.javet.values.reference.IV8ValueObject;
import com.caoccao.javet.values.reference.V8ValueProxy;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;
import me.fan87.javetzeroproxy.converter.AbstractV8ObjectPlugin;
import me.fan87.javetzeroproxy.converter.prototype.CustomJavetProxyPrototypeStore;

import java.util.List;

public class OPFunction extends AbstractV8ObjectPlugin {

    // FIXME: Function mode, does not work at all. I think, never tested it
    // (Also non of the annotation works :/ )
    @Override
    public V8Value apply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object, Aborter aborter) throws JavetException {
        aborter.abort();
        output.close();
        V8Value v8Value;
        if (object instanceof IJavetNonProxy) { // Ignore the proxy converter
            return runtime.createV8ValueUndefined();
        }

        // Determine the proxy mode
        V8ProxyMode proxyMode = V8ProxyMode.Function;
        Class<?> objectClass = object.getClass();

        try (V8Scope v8Scope = runtime.getV8Scope()) {
            V8ValueProxy v8ValueProxy;
            V8Value v8ValueTarget = null;
            try {
                v8ValueTarget = CustomJavetProxyPrototypeStore.createOrGetPrototype(converter, runtime, proxyMode, objectClass);
                v8ValueProxy = v8Scope.createV8ValueProxy(v8ValueTarget);
            } finally {
                JavetResourceUtils.safeClose(v8ValueTarget);
            }
            try (IV8ValueObject iV8ValueObjectHandler = v8ValueProxy.getHandler()) {
                IJavetProxyHandler<?, ?> javetProxyHandler;
                if (object instanceof IJavetDirectProxyHandler<?>) {
                    javetProxyHandler = new JavetDirectProxyFunctionHandler<>(runtime, (IJavetDirectProxyHandler<?>) object);
                } else {
                    javetProxyHandler = new JavetReflectionProxyFunctionHandler<>(runtime, object);
                }
                List<JavetCallbackContext> javetCallbackContexts = iV8ValueObjectHandler.bind(javetProxyHandler);
                try (V8ValueLong v8ValueLongHandle = runtime.createV8ValueLong(javetCallbackContexts.get(0).getHandle())) {
                    iV8ValueObjectHandler.setPrivateProperty(PRIVATE_PROPERTY_PROXY_TARGET, v8ValueLongHandle);
                }
            }
            v8Value = v8ValueProxy;
            v8Scope.setEscapable();
        }
        return v8Value;
    }

    @Override
    public boolean canApply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object) {
        Class<?> objectClass = object.getClass();
        return objectClass.isAnnotationPresent(V8Convert.class) && objectClass.getAnnotation(V8Convert.class).proxyMode() == V8ProxyMode.Function;
    }
}
