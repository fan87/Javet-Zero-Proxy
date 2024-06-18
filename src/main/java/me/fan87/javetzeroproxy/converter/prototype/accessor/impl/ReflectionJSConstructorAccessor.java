package me.fan87.javetzeroproxy.converter.prototype.accessor.impl;

import me.fan87.javetzeroproxy.converter.prototype.accessor.JSMethodAccessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ReflectionJSConstructorAccessor implements JSMethodAccessor {

    private final Constructor<?> constructor;

    public ReflectionJSConstructorAccessor(Constructor<?> constructor) {
        this.constructor = constructor;
    }

    @Override
    public Object invoke(Object receiver, Object[] value) {
        try {
            return constructor.newInstance(value);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
