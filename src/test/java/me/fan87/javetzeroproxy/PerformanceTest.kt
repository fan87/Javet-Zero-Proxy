package me.fan87.javetzeroproxy

import com.caoccao.javet.values.V8Value
import me.fan87.javetzeroproxy.classes.TestClass


object PerformanceTest {

    // 5000 times
    // Before optimization: 5454ms
    //  + lazy builtInObject: 3189ms
    //  + class caching: 2959ms
    //  + 0-Proxy (Except Bridge): 900ms
    //  + Proxy Handler Cache: 500ms
    //  + Object Reuse: 285ms


    fun getThread(thread: Thread): Thread {
        return Thread.currentThread()
    }

    val scriptManager = ScriptManager(
        proxyForBridgeSupport = false,
        useSameBinding = true
    )

    @JvmStatic
    fun main(args: Array<String>) {
        scriptManager.load()

        val executor =
            scriptManager.v8Runtime.getExecutor("return method.getParameters()[0].getDeclaringExecutable().getParameters()[0].getName()")
        val function = executor.compileV8ValueFunction(arrayOf("method"))
        val methodV8 = scriptManager.v8Runtime.toV8Value<Any, V8Value>(
            TestClass::class.java.getDeclaredMethod(
                "getThread",
                Thread::class.java
            )
        )
        scriptManager.bind(
            "method" to TestClass::class.java.getDeclaredMethod("getThread", Thread::class.java)
        ) {
            // Warm up
            println("Warming up (Making JIT working)")
            repeat (3000) {
                if (function.callString(null, methodV8) != "arg0") error("invalid output")
            }
            println("Warm-up has been completed")

            fun runTest() {
                val begin = System.currentTimeMillis()
                repeat(5000) {
                    if (it % 500 == 0) {
                        println(" Progress: ${it / 50}")
                    }
                    function.callString(null, methodV8)
                }
                val end = System.currentTimeMillis()
                println("Took: ${end - begin}ms")
            }
            // Actual
            runTest()
            runTest()
            runTest()
            runTest()

            println(" ===== Actual Test Begins ===== ")
            runTest()
            runTest()


        }

        scriptManager.unload()

    }
}
