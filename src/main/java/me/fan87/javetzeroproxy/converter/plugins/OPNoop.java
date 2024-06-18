package me.fan87.javetzeroproxy.converter.plugins;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.V8Value;
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter;
import me.fan87.javetzeroproxy.converter.AbstractV8ObjectPlugin;

public class OPNoop extends AbstractV8ObjectPlugin {
    @Override
    public V8Value apply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object, Aborter aborter) throws JavetException {
        return null;
    }

    @Override
    public boolean canApply(CustomJavetProxyConverter converter, V8Runtime runtime, V8Value output, Object object) {
        return false;
    }
}
