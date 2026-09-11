package com.houvven.impad

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.toClass
import io.github.libxposed.api.XposedModule
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal class DexMethodCache(
    private val module: XposedModule,
    private val prefs: SharedPreferences,
) {

    companion object {
        private const val TAG = "DexMethodCache"
    }

    private var dexkit: DexKitBridge? = null

    fun findOrLoad(
        cacheKey: String,
        classLoader: ClassLoader,
        finder: DexKitBridge.() -> DexMethod
    ): Method {
        loadCachedMethod(cacheKey, classLoader)?.let { return it }

        val foundMethod = requireDexkit(classLoader).finder()
        prefs.edit().putString(cacheKey, foundMethod.serialize()).apply()
        return foundMethod.getMethodInstance(classLoader).also {
            module.log(Log.DEBUG, TAG, "Found new method [$cacheKey]: $it")
        }
    }

    private fun loadCachedMethod(cacheKey: String, classLoader: ClassLoader): Method? {
        val serializedMethod =
            prefs.getString(cacheKey, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            DexMethod(serializedMethod).getMethodInstance(classLoader)
        }.onSuccess {
            module.log(Log.DEBUG, TAG, "Loaded cached method [$cacheKey]: $it")
        }.onFailure {
            prefs.edit().remove(cacheKey).apply()
            module.log(
                Log.WARN,
                TAG,
                "Cached method invalid [$cacheKey], fallback to DexKit: ${it.message}"
            )
        }.getOrNull()
    }

    private fun requireDexkit(classLoader: ClassLoader): DexKitBridge {
        dexkit?.let { return it }
        System.loadLibrary("dexkit")
        return DexKitBridge.create(classLoader, true).also { dexkit = it }
    }
}

internal fun XposedModule.afterApplicationAttach(
    tag: String = BuildConfig.APPLICATION_ID,
    applicationClass: Class<*> = Application::class.java,
    action: (Context) -> Unit
) {
    // Framework attach calls the application's attachBaseContext (including Tinker).
    // Run it unchanged first, then install against the final app context/classloader
    // before providers and onCreate. Never replace an application lifecycle method.
    val installed = AtomicBoolean(false)
    val method = applicationClass.getDeclaredMethod("attach", Context::class.java)
    hook(method).intercept { chain ->
        val result = chain.proceed()
        val context = (chain.thisObject as? Context) ?: (chain.args.firstOrNull() as? Context)
        if (context != null && installed.compareAndSet(false, true)) {
            runCatching { action(context) }.onFailure {
                log(Log.ERROR, tag, "Application attach hook failed: ${it.javaClass.simpleName}")
            }
        }
        result
    }
}

internal fun XposedModule.hookToReturn(method: Method, value: Any?) {
    hook(method).intercept { value }
}

internal fun XposedModule.hookAllToReturn(methods: Iterable<Method>, value: Any?) {
    methods.forEach { hookToReturn(it, value) }
}

internal fun XposedModule.simulateTabletProperties(characteristics: String = "tablet") {
    "android.os.SystemProperties".toClass().resolve().method {
        name("get")
        returnType(String::class)
    }.forEach { method ->
        hook(method.self).intercept { chain ->
            if (chain.args.firstOrNull() == "ro.build.characteristics") {
                return@intercept characteristics
            }
            return@intercept chain.proceed()
        }
    }
}

internal fun simulateTabletModel(
    brand: String,
    model: String,
    manufacturer: String = brand
) {
    android.os.Build::class.resolve().run {
        firstField { name("MANUFACTURER") }.set(manufacturer)
        firstField { name("BRAND") }.set(brand)
        firstField { name("MODEL") }.set(model)
    }
}
