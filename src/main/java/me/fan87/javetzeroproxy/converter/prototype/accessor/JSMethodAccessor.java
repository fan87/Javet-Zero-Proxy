package me.fan87.javetzeroproxy.converter.prototype.accessor;

// TODO: use faster method access methods (instead of reflection)
public interface JSMethodAccessor {

    Object invoke(Object receiver, Object[] value);

}
