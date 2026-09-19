package com.houvven.impad

import android.content.Context
import android.app.Application
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterfaceWrapper
import io.github.libxposed.api.XposedModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.reflect.Proxy

/** Exercise the real hook adapter against a fake external LSPosed boundary. */
class ApplicationLifecycleTest {
    // Android's mockable SDK jar strips hidden Application.attach. Substitute only
    // this external class boundary; the production registration/interceptor is real.
    class FrameworkApplication {
        fun attach(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
    }

    @Test
    fun `lifecycle hook preserves original execution and result without a context`() {
        val installedHook = install { error("No context: must not install app hooks") }
        var originalCalls = 0
        val expectedResult = Any()
        val result = installedHook.intercept(chain(null) { originalCalls++; expectedResult })
        assertEquals(1, originalCalls)
        assertSame(expectedResult, result)
    }

    @Test
    fun `failed original attach is propagated without installing app hooks`() {
        var installs = 0
        val installedHook = install { installs++ }
        val failure = IllegalStateException("original attach failure")
        val actual = assertThrows(IllegalStateException::class.java) {
            installedHook.intercept(chain(context()) { throw failure })
        }
        assertSame(failure, actual)
        assertEquals(0, installs)
    }

    @Test
    fun `app hooks run after attach once and cannot suppress later original calls`() {
        val events = mutableListOf<String>()
        val appContext = context()
        val installedHook = install {
            assertSame(appContext, it)
            events.add("install")
            throw IllegalStateException("hook installation failed")
        }
        val originalResult = Any()
        val chain = chain(appContext) { events.add("original"); originalResult }
        assertSame(originalResult, installedHook.intercept(chain))
        assertSame(originalResult, installedHook.intercept(chain))
        assertEquals(listOf("original", "install", "original"), events)
    }

    private fun install(action: (Context) -> Unit): XposedInterface.Hooker {
        lateinit var installedHook: XposedInterface.Hooker
        val builder = proxy<XposedInterface.HookBuilder> { name, args ->
            check(name == "intercept")
            installedHook = args!![0] as XposedInterface.Hooker
            null
        }
        val framework = proxy<XposedInterface> { name, _ ->
            when (name) {
                "hook" -> builder
                "log" -> null
                else -> error("Unexpected framework call: $name")
            }
        }
        val module = object : XposedModule() {}
        // Only the test stands in for the framework. Production never calls this
        // internal API; API 102 requires the framework's detach callback as well.
        XposedInterfaceWrapper::class.java.getDeclaredMethod(
            "attachFramework", XposedInterface::class.java, Runnable::class.java
        ).apply { isAccessible = true }.invoke(module, framework, Runnable {
            error("Installing a lifecycle hook must not detach the module")
        })
        module.afterApplicationAttach(applicationClass = FrameworkApplication::class.java, action = action)
        return installedHook
    }

    private fun chain(context: Context?, proceed: () -> Any?): XposedInterface.Chain =
        proxy<XposedInterface.Chain> { name, _ ->
            when (name) {
                "proceed" -> proceed()
                "getArgs" -> emptyList<Any?>()
                "getThisObject" -> context
                else -> error("Unexpected chain call: $name")
            }
        }

    private fun context(): Context {
        // External Android object is only passed by identity, never used as a real
        // running Application. Avoid executing the SDK stub's throwing constructor.
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return (field.get(null) as sun.misc.Unsafe).allocateInstance(Application::class.java) as Context
    }

    private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            invoke(method.name, args)
        } as T
}
