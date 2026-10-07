package com.houvven.impad

import android.content.Context
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterfaceWrapper
import io.github.libxposed.api.XposedModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** Substitute QQ/LSPosed boundaries while exercising the real production adapter. */
class QQTabletClassifierTest {
    enum class DeviceType { PHONE, TABLET, FOLD }
    enum class UnknownDeviceType { PHONE }

    class QQPadUtil {
        companion object {
            @JvmStatic fun classify(@Suppress("UNUSED_PARAMETER") context: Context?) = DeviceType.PHONE
            @JvmStatic fun unrelated() = DeviceType.PHONE
        }
        fun instanceClassifier(@Suppress("UNUSED_PARAMETER") context: Context?) = DeviceType.PHONE
    }
    class AmbiguousQQPadUtil {
        companion object {
            @JvmStatic fun first(@Suppress("UNUSED_PARAMETER") context: Context?) = DeviceType.PHONE
            @JvmStatic fun second(@Suppress("UNUSED_PARAMETER") context: Context?) = DeviceType.PHONE
        }
    }
    class QQAppSetting {
        companion object {
            var calls = 0
            @JvmStatic fun e(): Int { calls++; return 123 }
        }
    }

    @Test
    fun `login observation never calls or overrides the QQ getter`() {
        val fixture = framework()
        QQAppSetting.calls = 0
        fixture.module.observeQQLoginAppId(QQAppSetting::class.java, 456)
        assertEquals(0, QQAppSetting.calls)
        val observer = fixture.hooks.single()
        assertEquals(123, observer.intercept(chain { QQAppSetting.e() }))
        assertEquals(1, QQAppSetting.calls)
        val failure = IllegalStateException("QQ login unavailable")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            observer.intercept(chain { throw failure })
        })
    }

    @Test
    fun `only hooks the QQ classifier and preserves its original initialization`() {
        val fixture = framework()
        fixture.module.installQQTabletClassifier(QQPadUtil::class.java, DeviceType::class.java)
        assertEquals(listOf(QQPadUtil::class.java.getDeclaredMethod("classify", Context::class.java)), fixture.methods)
        var calls = 0
        val hook = fixture.hooks.single()
        repeat(2) {
            assertSame(DeviceType.TABLET, hook.intercept(chain { calls++; DeviceType.PHONE }))
        }
        assertEquals(2, calls)
        assertSame(DeviceType.PHONE, QQPadUtil.unrelated())
    }

    @Test
    fun `original classifier failure is propagated`() {
        val fixture = framework()
        fixture.module.installQQTabletClassifier(QQPadUtil::class.java, DeviceType::class.java)
        val failure = IllegalStateException("QQ initialization failed")
        val actual = assertThrows(IllegalStateException::class.java) {
            fixture.hooks.single().intercept(chain { throw failure })
        }
        assertSame(failure, actual)
    }

    @Test
    fun `changed QQ layout with two candidates installs no hook`() {
        val fixture = framework()
        assertThrows(IllegalArgumentException::class.java) {
            fixture.module.installQQTabletClassifier(AmbiguousQQPadUtil::class.java, DeviceType::class.java)
        }
        assertEquals(emptyList<Method>(), fixture.methods)
    }

    @Test
    fun `missing tablet enum installs no hook`() {
        val fixture = framework()
        assertThrows(NoSuchElementException::class.java) {
            fixture.module.installQQTabletClassifier(QQPadUtil::class.java, UnknownDeviceType::class.java)
        }
        assertEquals(emptyList<Method>(), fixture.methods)
    }

    private data class Fixture(
        val module: XposedModule,
        val methods: MutableList<Method>,
        val hooks: MutableList<XposedInterface.Hooker>
    )

    private fun framework(): Fixture {
        val methods = mutableListOf<Method>()
        val hooks = mutableListOf<XposedInterface.Hooker>()
        val builder = proxy<XposedInterface.HookBuilder> { name, args ->
            check(name == "intercept")
            hooks.add(args!![0] as XposedInterface.Hooker)
            null
        }
        val framework = proxy<XposedInterface> { name, args ->
            when (name) {
                "hook" -> { methods.add(args!![0] as Method); builder }
                "log" -> null
                else -> error("Unexpected framework call: $name")
            }
        }
        val module = object : XposedModule() {}
        XposedInterfaceWrapper::class.java.getDeclaredMethod(
            "attachFramework", XposedInterface::class.java, Runnable::class.java
        ).apply { isAccessible = true }.invoke(module, framework, Runnable {})
        return Fixture(module, methods, hooks)
    }

    private fun chain(proceed: () -> Any?): XposedInterface.Chain =
        proxy<XposedInterface.Chain> { name, _ ->
            when (name) {
                "proceed" -> proceed()
                else -> error("Unexpected chain call: $name")
            }
        }

    private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            invoke(method.name, args)
        } as T
}
