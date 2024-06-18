package com.caoccao.javet.values.reference;

import com.caoccao.javet.enums.V8ValueReferenceType;
import com.caoccao.javet.exceptions.JavetException;

public class CloseHandler extends V8ValueReference {
    private final Callback callback;
    private final V8ValueReferenceType type;

    public CloseHandler(V8ValueReference parent, Callback callback) throws JavetException {
        super(parent.getV8Runtime(), ((V8ValueReference) parent.toClone(true)).getHandle());
        this.callback = callback;
        this.type = parent.getType();
        this.setWeak();
    }

    @Override
    public V8ValueReferenceType getType() {
        return type;
    }



    @Override
    public void close() throws JavetException {
        super.close();
    }

    @Override
    public void close(boolean forceClose) throws JavetException {
        super.close(forceClose);
        callback.onClosed();
    }

    public interface Callback {
        void onClosed();
    }
}
