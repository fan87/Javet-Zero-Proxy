package me.fan87.javetzeroproxy.converter.plugins.post;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.proxy.IJavetDirectProxyHandler;
import com.caoccao.javet.values.V8Value;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;
import me.fan87.javetzeroproxy.converter.AbstractV8ObjectPlugin;
import me.fan87.javetzeroproxy.converter.plugins.pre.OPProxyPlugin;

public class OPIJavetDirectProxyHandler extends AbstractV8ObjectPlugin {
    @Override
    public V8Value apply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object, Aborter aborter) throws JavetException {
        IJavetDirectProxyHandler<?> javetDirectProxyHandler = ((IJavetDirectProxyHandler<?>) object);
        aborter.abort();
        output.close();

        V8Value originalBinding = converter.getBindingOf(object);
        if (originalBinding != null) return originalBinding;

        return OPProxyPlugin.applyProxyHandler(converter, runtime, object, javetDirectProxyHandler);
    }

    @Override
    public boolean canApply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object) {
        return object instanceof IJavetDirectProxyHandler<?>;
    }
}
