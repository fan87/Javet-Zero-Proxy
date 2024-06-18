package me.fan87.javetzeroproxy.converter.plugins.post;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.proxy.IJavetNonProxy;
import com.caoccao.javet.values.V8Value;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;
import me.fan87.javetzeroproxy.converter.AbstractV8ObjectPlugin;

public class OPNonProxy extends AbstractV8ObjectPlugin {
    @Override
    public V8Value apply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object, Aborter aborter) throws JavetException {
        output.close();
        aborter.abort();
        return runtime.createV8ValueUndefined();
    }

    @Override
    public boolean canApply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object) {
        return object instanceof IJavetNonProxy;
    }
}
