package me.fan87.javetzeroproxy.converter.wrapper

fun Class<*>.toV8StaticClass(): ClassWrapper = ClassWrapper(this)