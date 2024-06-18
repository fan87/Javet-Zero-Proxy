package me.fan87.javetzeroproxy.mapping

import java.lang.reflect.Field
import java.lang.reflect.Method

interface IJZPMapper {

    fun getPossibleMethodName(method: Method): List<String>
    fun getPossibleFieldName(field: Field): List<String>

}