package com.houvven.impad

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/** Resolve the QQ-owned enum classifier rather than replace platform identity APIs. */
internal fun XposedModule.installQQTabletClassifier(
    padUtilClass: Class<*>,
    deviceTypeClass: Class<*>
) {
    require(deviceTypeClass.isEnum) { "QQ device type is not an enum" }
    val tablet = requireNotNull(deviceTypeClass.enumConstants).single { (it as Enum<*>).name == "TABLET" }
    val classifier = padUtilClass.declaredMethods.filter { method ->
        Modifier.isPublic(method.modifiers) && Modifier.isStatic(method.modifiers) &&
            method.returnType == deviceTypeClass &&
            method.parameterTypes.contentEquals(arrayOf(Context::class.java))
    }.single()
    val loggedFirstHit = AtomicBoolean(false)
    hook(classifier).intercept { chain ->
        // Preserve initialization, cache maintenance, and original exceptions.
        val original = chain.proceed()
        if (loggedFirstHit.compareAndSet(false, true)) {
            log(
                Log.INFO, BuildConfig.APPLICATION_ID,
                "[IAmPad-QQ] classifier hit: original=${(original as? Enum<*>)?.name}, " +
                    "result=TABLET, brand=${Build.BRAND}, manufacturer=${Build.MANUFACTURER}, " +
                    "model=${Build.MODEL}, device=${Build.DEVICE}"
            )
        }
        tablet
    }
}

/** Observe natural calls only: invoking AppSetting early can freeze its manifest defaults. */
internal fun XposedModule.installQQLoginObserver(context: Context) {
    runCatching {
        val appSetting = Class.forName("com.tencent.common.config.AppSetting", false, context.classLoader)
        val metadata = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        val expectedPadId = Regex("^\\d+").find(metadata?.getString("AppSetting_params_pad").orEmpty())?.value?.toIntOrNull()
        observeQQLoginAppId(appSetting, expectedPadId)
    }.onFailure {
        log(Log.WARN, BuildConfig.APPLICATION_ID, "[IAmPad-QQ] login observation unavailable: ${it.javaClass.simpleName}")
    }
}

internal fun XposedModule.observeQQLoginAppId(appSettingClass: Class<*>, expectedPadId: Int?) {
    val getter = appSettingClass.getDeclaredMethod("e")
    require(Modifier.isStatic(getter.modifiers) && getter.returnType == Int::class.javaPrimitiveType)
    val loggedFirstCall = AtomicBoolean(false)
    hook(getter).intercept { chain ->
        val result = chain.proceed()
        if (loggedFirstCall.compareAndSet(false, true)) {
            log(
                Log.INFO, BuildConfig.APPLICATION_ID,
                "[IAmPad-QQ] natural login decision: appId=$result, " +
                    "manifestPadId=$expectedPadId, padAppIdMatches=${expectedPadId != null && result == expectedPadId}"
            )
        }
        result
    }
}
