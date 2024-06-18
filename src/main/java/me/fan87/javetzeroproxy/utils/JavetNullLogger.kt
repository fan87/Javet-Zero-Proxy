package me.fan87.javetzeroproxy.utils

import com.caoccao.javet.interfaces.IJavetLogger

object JavetNullLogger : IJavetLogger {
    override fun debug(message: String?) {

    }

    override fun error(message: String?) {
    }

    override fun error(message: String?, cause: Throwable?) {
    }

    override fun info(message: String?) {
    }

    override fun warn(message: String?) {
    }
}