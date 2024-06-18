package me.fan87.javetzeroproxy.mapping

import java.io.Reader
import java.lang.reflect.Field
import java.lang.reflect.Method

class SRGMethodFieldMapper(mapping: Reader?) : IJZPMapper {

    private val methodNames = HashMap<String, String>()
    private val fieldNames = HashMap<String, String>()

    init {
        mapping?.forEachLine {
            if (it.startsWith("#")) return@forEachLine
            if (it.startsWith("CL: ")) return@forEachLine
            if (it.startsWith("FD: ")) {
                val split = it.split(" ")
                fieldNames[split[1].substringAfterLast('/')] =
                    split[2].substringAfterLast('/')
            }
            if (it.startsWith("MD: ")) {
                val split = it.split(" ")
                methodNames[split[1].substringAfterLast('/')] =
                    split[3].substringAfterLast('/')
            }
        }
    }

    override fun getPossibleMethodName(method: Method): List<String> {
        return methodNames[method.name]?.let { listOf(method.name, it) } ?: listOf(method.name)
    }
    override fun getPossibleFieldName(field: Field): List<String> {
        return fieldNames[field.name]?.let { listOf(field.name, it) } ?: listOf(field.name)
    }

}