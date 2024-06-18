package me.fan87.javetzeroproxy.converter.prototype.accessor.impl;

import me.fan87.javetzeroproxy.converter.prototype.accessor.JSMethodAccessor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionJSMethodAccessor implements JSMethodAccessor {

    private final Method method;

    public ReflectionJSMethodAccessor(Method method) {
        this.method = method;
    }

    @Override
    public Object invoke(Object receiver, Object[] value) {
        try {
            method.setAccessible(true);
            return method.invoke(receiver, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
