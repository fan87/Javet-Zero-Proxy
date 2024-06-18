package me.fan87.javetzeroproxy.converter.prototype.accessor.impl;

import me.fan87.javetzeroproxy.converter.prototype.accessor.JSFieldAccessor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

public class ReflectionJSSetterGetterFieldAccessor implements JSFieldAccessor {

    private final Method getter;
    private final Method setter;

    public ReflectionJSSetterGetterFieldAccessor(Method getter, Method setter) {
        this.getter = Objects.requireNonNull(getter);
        this.setter = setter;
        if (setter != null) {
            if (Modifier.isStatic(setter.getModifiers()) != Modifier.isStatic(getter.getModifiers())) {
                throw new IllegalArgumentException("inconsistent static modifier");
            }
        }
    }

    @Override
    public Object get(Object receiver) {
        try {
            return getter.invoke(receiver);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object set(Object receiver, Object value) {
        try {
            if (isFinal()) throw new RuntimeException("cannot set.");
            setter.invoke(receiver, value);
            return value;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isFinal() {
        return setter == null;
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(getter.getModifiers());
    }
}
