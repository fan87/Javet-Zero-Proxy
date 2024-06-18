package me.fan87.javetzeroproxy.converter.prototype.accessor;

// TODO: use faster fields access methods
public interface JSFieldAccessor {

    Object get(Object receiver);
    Object set(Object receiver, Object value);

    boolean isFinal();
    boolean isStatic();

}
