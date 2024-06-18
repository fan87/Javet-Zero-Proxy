package me.fan87.javetzeroproxy.converter;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.values.reference.CloseHandler;
import com.caoccao.javet.values.reference.V8ValueObject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GCHandler extends Thread {

    private final CustomJavetProxyConverter converter;
    private final V8Runtime runtime;
    public final BlockingQueue<Long> closed = new LinkedBlockingQueue<>();
    public final Set<Long> skip = new HashSet<>();

    public GCHandler(CustomJavetProxyConverter converter, V8Runtime runtime) {
        this.converter = converter;
        this.runtime = runtime;
    }

    public void add(long handle, V8ValueObject object) {
        try {
            new CloseHandler(object, () -> {
                closed.add(handle);
                skip.remove(handle);
            });
        } catch (JavetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (!runtime.isClosed()) {
            try {
                Long ref = closed.take();
                if (!skip.remove(ref)) {
                    converter.deleteBinding(ref);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public void deleteNow(long ref) {
        converter.deleteBinding(ref);
        skip.add(ref);
    }
}
