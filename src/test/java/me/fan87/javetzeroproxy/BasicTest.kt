package me.fan87.javetzeroproxy

import com.caoccao.javet.exceptions.JavetException
import com.caoccao.javet.interop.executors.IV8Executor
import com.caoccao.javet.values.reference.V8ValueObject
import me.fan87.javetzeroproxy.classes.TestClass
import me.fan87.javetzeroproxy.classes.TestClass2
import me.fan87.javetzeroproxy.converter.wrapper.toV8StaticClass
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class BasicTest {


    private fun getExecutor(script: String): IV8Executor {
        return scriptManager.v8Runtime.getExecutor(script)
    }

    @Test
    fun `Test GC`() {
        val obj = getExecutor("new Java.me.fan87.javetzeroproxy.classes.TestClass()").execute<V8ValueObject>()
        Assertions.assertFalse(obj.isClosed)
        obj.setWeak()
        Assertions.assertFalse(obj.isClosed)
        scriptManager.v8Runtime.lowMemoryNotification()
        System.gc()
        scriptManager.v8Runtime.lowMemoryNotification()
        Assertions.assertTrue(obj.isClosed)
    }

    @Test
    fun `Test memory leak`() {
        isLeaked(TestClass()) // Test OPObject
        isLeaked(System.out) // Test OPObject
        isLeaked(hashMapOf("" to "")) // Test OPProxyPlugin
        isLeaked(System.getProperties()) // Test OPProxyPlugin
        // We don't need to worry about ReflectionClass and Class, as they are cached
    }

    @Test
    @Throws(JavetException::class)
    fun `Test inheritance`() {
        scriptManager.bind(
            "obj" to TestClass2(),
            "obj1" to TestClass(),
        ) {
            Assertions.assertEquals(87, getExecutor("obj.getNumber()").executeInteger())
            Assertions.assertEquals(2, getExecutor("obj.variableA").executeInteger())
            Assertions.assertEquals(1, getExecutor("obj1.variableA").executeInteger())
            Assertions.assertFalse(getExecutor("obj.hasOwnProperty('getNumber')").executeBoolean())
            Assertions.assertFalse(getExecutor("obj.__proto__.hasOwnProperty('getNumber')").executeBoolean())
            Assertions.assertTrue(getExecutor("obj.__proto__.__proto__.hasOwnProperty('getNumber')").executeBoolean())
            Assertions.assertEquals(TestClass2::class.java.name, getExecutor("obj.getClass().getName()").executeString())
        }
    }
    @Test
    @Throws(JavetException::class)
    fun `Test compiled function`() {
        val compiled =
            getExecutor("return obj.getNumber()").compileV8ValueFunction(arrayOf("obj", "obj1"))
        scriptManager.v8Runtime.lowMemoryNotification()
        println(compiled.isClosed)
        Assertions.assertEquals(87, compiled.callInteger(null, TestClass2()))
    }
    @Test
    @Throws(JavetException::class)
    fun `JS Inheritance test`() {
        scriptManager.bind(
            "TestClass" to TestClass::class.java.toV8StaticClass(),
        ) {
            Assertions.assertEquals(88, getExecutor("""
                class Example extends TestClass {
                    getNumber() {
                        return 88;
                    } 
                    getSuperNumber() {
                        return super.getNumber();
                    } 
                    constructor() { 
                        super(); 
                    } 
                }
                (new Example()).getNumber()
            """.trimIndent()).executeInteger())

            Assertions.assertEquals(1, getExecutor("""
                (new Example()).variableA
            """.trimIndent()).executeInteger())
            Assertions.assertEquals(2, getExecutor("""
                let obj = (new Example());
                obj.variableA = 2;
                obj.variableA
            """.trimIndent()).executeInteger())
            Assertions.assertEquals(87, getExecutor("""
                obj.getSuperNumber()
            """.trimIndent()).executeInteger())
        }
    }

    private fun isLeaked(target: Any) {
        val obj: V8ValueObject = scriptManager.v8Runtime.toV8Value(target)
        obj.setWeak()
        Assertions.assertFalse(obj.isClosed)
        scriptManager.v8Runtime.lowMemoryNotification()
        System.gc()
        scriptManager.v8Runtime.lowMemoryNotification()
        Assertions.assertTrue(obj.isClosed)
    }

    @Test
    @Throws(JavetException::class)
    fun testClass() {
        System.getProperties()
        scriptManager.bind("System" to System::class.java.toV8StaticClass()) {
            getExecutor("System.getProperties().set('hello', 'world')").executeVoid()
            Assertions.assertEquals("world", getExecutor("System.getProperties().get('hello')").executeString())
            Assertions.assertEquals("world", System.getProperties()["hello"])
        }
        val array = arrayOf("hello", "world")
        scriptManager.bind("arr" to array) {
            getExecutor("arr[0] = 'world'").executeVoid()
            Assertions.assertEquals("world", getExecutor("arr[0]").executeString())
            Assertions.assertEquals("world", array[0])
        }
        Assertions.assertTrue(getExecutor("javet.package.me.fan87.javetzeroproxy.classes.TestClass.class.getMethod('getThread', javet.package.java.lang.Thread.class).getParameters()[0].getDeclaringExecutable().getParameters()[0].getName()").executeBoolean())
    }

    companion object {
        private val scriptManager = ScriptManager(
            proxyForBridgeSupport = true,
            useSameBinding = true
        )

        @JvmStatic
        @BeforeAll
        fun initTests() {
            scriptManager.load()
        }
        @JvmStatic
        @AfterAll
        fun cleanupTests() {
            scriptManager.unload()
        }
    }

}