package me.fan87.javetzeroproxy.converter.prototype.accessor.impl;

import me.fan87.javetzeroproxy.converter.prototype.accessor.JSFieldAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ReflectionJSFieldAccessor implements JSFieldAccessor {
    
    private final Field field;

    public ReflectionJSFieldAccessor(Field field) {
        this.field = field;
    }

    @Override
    public Object get(Object receiver) {
        try {
            return field.get(receiver);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object set(Object receiver, Object value) {
        try {
            if (isFinal()) throw new RuntimeException("cannot set.");
            field.set(receiver, value);
            return value;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isFinal() {
        return Modifier.isFinal(field.getModifiers());
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(field.getModifiers());
    }
}
