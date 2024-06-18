package me.fan87.javetzeroproxy

import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.utils.JavetResourceUtils
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueGlobalObject
import com.caoccao.javet.values.reference.builtin.V8ValueBuiltInObject
import me.fan87.javetzeroproxy.converter.CustomJavetProxyConverter
import me.fan87.javetzeroproxy.mapping.IJZPMapper
import me.fan87.javetzeroproxy.mapping.SRGMethodFieldMapper
import me.fan87.javetzeroproxy.utils.JavetNullLogger

open class ScriptManager(
    val mapper: IJZPMapper = SRGMethodFieldMapper(null),
    val proxyForBridgeSupport: Boolean = true,
    val useSameBinding: Boolean = true,
    val withInterceptor: Boolean = true,
    val logging: Boolean = true
) {


    lateinit var engine: V8Host
        private set
    lateinit var v8Runtime: V8Runtime
        private set
    lateinit var interceptor: CustomJavetJVMInterceptor
        private set

    var globalObject: V8ValueGlobalObject? = null
        private set
    var builtInObject: V8ValueBuiltInObject? = null
        private set

    fun load() {
        engine = V8Host.getV8Instance()
        v8Runtime = engine.createV8Runtime()
        if (!logging) {
            v8Runtime.logger = JavetNullLogger
        }
        v8Runtime.converter = CustomJavetProxyConverter(v8Runtime, this)
        globalObject = v8Runtime.globalObject
        builtInObject = v8Runtime.globalObject.builtInObject

        if (withInterceptor) {
            interceptor = CustomJavetJVMInterceptor(v8Runtime)
            interceptor.register(v8Runtime.globalObject)
        }
    }

    fun unload() {
        if (withInterceptor) {
            interceptor.unregister(v8Runtime.globalObject)
        }
        (v8Runtime.converter as CustomJavetProxyConverter).close()
        v8Runtime.close()
    }

    inline fun bind(vararg param: Pair<String, Any>, block: () -> Unit) {
        val has = param.map { v8Runtime.globalObject.has(it.first) }
        val oldObjects = param.map {
            if (v8Runtime.globalObject.has(it.first))
                v8Runtime.globalObject.get<V8Value>(it.first)
            else
                null
        }
        try {
            for ((key, obj) in param) {
                v8Runtime.globalObject.set(key, obj)
            }
            block()
        } finally {
            // Recover
            for ((index, pair) in param.withIndex()) {
                if (has[index]) {
                    val old = oldObjects[index]
                    v8Runtime.globalObject.set(pair.first, old)
                } else {
                    v8Runtime.globalObject.delete(pair.first)
                }
            }
            JavetResourceUtils.safeClose(oldObjects)
        }
    }


}