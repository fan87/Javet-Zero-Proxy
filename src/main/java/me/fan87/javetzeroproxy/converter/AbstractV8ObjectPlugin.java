package me.fan87.javetzeroproxy.converter;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.builtin.V8ValueBuiltInObject;
import me.fan87.javetzeroproxy.ScriptManager;

public abstract class AbstractV8ObjectPlugin {
    protected static final String PRIVATE_PROPERTY_PROXY_TARGET = "Javet#proxyTarget";

    ScriptManager scriptManager;

    public abstract V8Value apply(
        CustomJavetProxyConverter converter,
        V8Runtime runtime,
        V8Value output,
        Object object,
        Aborter aborter
    ) throws JavetException;

    public Object tryRecoverOriginal(
        CustomJavetProxyConverter converter,
        V8Runtime runtime,
        V8Value value
    ) throws JavetException {
        return null;
    }

    protected V8ValueBuiltInObject getBuiltinObject(V8Runtime v8Runtime) throws JavetException {
        V8ValueBuiltInObject obj = (v8Runtime == scriptManager.getV8Runtime()) ?
            scriptManager.getBuiltInObject() :
            v8Runtime.getGlobalObject().getBuiltInObject();
        assert obj != null;
        return obj;
    }

    public abstract boolean canApply(
        CustomJavetProxyConverter converter,
        V8Runtime runtime,
        V8Value output,
        Object object
    );
    public boolean canRecover(
        CustomJavetProxyConverter converter,
        V8Runtime runtime,
        V8Value value
    ) throws JavetException {
        return false;
    }

    public interface Aborter {
        void abort();
    }

}
