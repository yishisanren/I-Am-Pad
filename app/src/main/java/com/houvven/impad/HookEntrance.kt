package com.houvven.impad

import android.app.Activity
import android.content.Context
import android.os.Process
import android.util.Log
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.hasClass
import com.highcapable.kavaref.extension.toClass
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation

class HookEntrance : XposedModule() {

    companion object {
        private const val TAG = BuildConfig.APPLICATION_ID
        private const val DEXKIT_PREFS_NAME = "IAMPAD_dexkit"
        private const val QQ_TARGET_MODEL = "23046RP50C"
        private const val XHS_TARGET_MODEL = "23046RP50C"
    }

    private var methodCache: DexMethodCache? = null
    @Suppress("SpellCheckingInspection")
    private val customWeWorkPackages = setOf(
        "com.airchina.wecompro",
        "com.zwfw.YueZhengYi",
        "com.cscec.portal",
        "cn.powerchina.pact"
    )

    private data class PackageRoute(
        val match: (XposedModuleInterface.PackageReadyParam) -> Boolean,
        val handle: (XposedModuleInterface.PackageReadyParam) -> Unit
    )

    private val packageRoutes = listOf(
        PackageRoute(
            match = { PackageTargetResolver.resolve(it.packageName) == PackageTarget.FEISHU },
            handle = { processFeishu(it.classLoader) }
        ),
        PackageRoute(
            match = { it.packageName.contains("com.tencent.mobileqq") },
            handle = { processQQ() }
        ),
        PackageRoute(
            match = { it.packageName.contains("com.tencent.mm") },
            handle = { processWeChat() }
        ),
        PackageRoute(
            match = { it.packageName.contains("com.tencent.wework") },
            handle = { processWeWork() }
        ),
        PackageRoute(
            match = { it.packageName.contains("com.xingin.xhs") },
            handle = { processXhs(it.classLoader) }
        ),
        PackageRoute(
            match = ::isDingTalk,
            handle = { processDingTalk() }
        ),
        PackageRoute(
            match = ::isCustomWeWork,
            handle = { processCustomWeWork() }
        )
    )

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val route = packageRoutes.firstOrNull { it.match(param) } ?: return
        log(Log.INFO, TAG, "[IAmPad-startup] package=${param.packageName} pid=${Process.myPid()} version=${BuildConfig.VERSION_NAME}")
        runCatching { route.handle(param) }.onFailure {
            log(Log.ERROR, TAG, "[IAmPad-startup] installation-failed package=${param.packageName} type=${it.javaClass.simpleName}")
        }
    }

    private fun processQQ() {
        simulateTabletModel("Xiaomi", QQ_TARGET_MODEL)
        simulateTabletProperties()
        // Preserve QQ's persisted account/device state. A telemetry cache mismatch
        // must never trigger automatic MMKV deletion or a process-kill loop.
        log(Log.INFO, TAG, "Installed QQ tablet identity: model=${android.os.Build.MODEL}")
    }

    private fun processWeChat() = afterApplicationAttach { context ->
        hookDexMethodToReturn("checkLoginAsPad_method", context, true) {
            findMethod {
                excludePackages("android", "androidx", "com")
                matcher {
                    modifiers(Modifier.PUBLIC or Modifier.FINAL)
                    paramCount(3)
                    paramTypes(
                        String::class.java,
                        String::class.java,
                        Continuation::class.java
                    )
                    usingStrings(
                        "MicroMsg.CgiCheckLoginAsPad",
                        "/cgi-bin/micromsg-bin/checkloginaspad"
                    )
                }
            }.single().toDexMethod()
        }

        hookDexMethodToReturn("isFoldableDevice_method", context, true) {
            findMethod {
                searchPackages("com.tencent.mm.ui")
                matcher {
                    modifiers(Modifier.PUBLIC or Modifier.STATIC)
                    paramCount(0)
                    usingStrings("royole", "tecno", "ro.os_foldable_screen_support")
                    returnType(Boolean::class.javaPrimitiveType!!)
                }
            }.single().toDexMethod()
        }
    }

    private fun processWeWork() = afterApplicationAttach { context ->
        hookAllToReturn(
            methods = "com.tencent.wework.foundation.impl.WeworkServiceImpl"
                .toClass(context.classLoader)
                .resolve()
                .method {
                    name { it.startsWith("isAndroidPad") }
                    returnType(Boolean::class)
                }.map { it.self },
            value = true
        )
    }

    private fun processDingTalk() = afterApplicationAttach { context ->
        hookDexMethodToReturn("isMultiLoginFoldableDevice_method", context, true) {
            findMethod {
                searchPackages("com.alibaba.android.dingtalkbase.foldable")
                matcher {
                    modifiers(Modifier.STATIC)
                    paramCount(1)
                    paramTypes(Activity::class.java)
                    returnType(Boolean::class.javaPrimitiveType!!)
                    usingStrings("isMultiLoginFoldableDevice")
                }
            }.single().toDexMethod()
        }
    }

    private fun processCustomWeWork() = afterApplicationAttach { context ->
        hookDexMethodToReturn("isPadJudge_method", context, true) {
            findMethod {
                matcher {
                    declaredClass("com.tencent.wework.common.utils.WwUtil")
                    returnType(Boolean::class.javaPrimitiveType!!)
                    paramCount(0)
                    modifiers(Modifier.STATIC)
                    usingStrings(
                        "isPadJudge",
                        "isPadWhiteListFromServer", "isPadBlackListFromServer",
                        "isPadWhiteListFromLocal", "isPadBlackListFromLocal"
                    )
                }
            }.single().toDexMethod()
        }
    }

    private fun processXhs(classLoader: ClassLoader) {
        runCatching {
            // XHS decides the device type via the server-side classification: the
            // accurate model is sent to /api/sns/v1/system/device_type and the reply
            // is cached in key_device_type_from_cloud. Spoof the model sent in the
            // request and pin the local device type to "pad".
            "com.xingin.adaptation.device.DeviceInfoContainer".toClass(classLoader).resolve().run {
                val deviceTypeMethods = method { name("getDeviceType") }.map { it.self }
                val savedDeviceTypeMethods = method { name("getSavedDeviceType") }.map { it.self }
                val accurateModelMethods = method { name("getDeviceAccurateModel") }.map { it.self }

                hookAllToReturn(deviceTypeMethods, "pad")
                hookAllToReturn(savedDeviceTypeMethods, "pad")
                hookAllToReturn(accurateModelMethods, XHS_TARGET_MODEL)

                log(
                    Log.INFO,
                    TAG,
                    "Installed XHS pad hooks: getDeviceType=${deviceTypeMethods.size}, " +
                        "getSavedDeviceType=${savedDeviceTypeMethods.size}, " +
                        "getDeviceAccurateModel=${accurateModelMethods.size}, " +
                        "model=$XHS_TARGET_MODEL"
                )
            }
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to install XHS hooks: ${it.stackTraceToString()}")
        }
    }

    private fun processFeishu(classLoader: ClassLoader) {
        runCatching {
            // Feishu 7.76.14 derives both passport DeviceInfo.deviceModel and the
            // X-Device-Info login header from Build.MODEL. Its local tablet check
            // reads ro.build.characteristics and looks for "tablet".
            simulateTabletModel(FeishuTabletProfile.brand, FeishuTabletProfile.model)
            simulateTabletProperties(FeishuTabletProfile.buildCharacteristics)
            val reportedCharacteristics = Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, "ro.build.characteristics")
            log(
                Log.INFO,
                TAG,
                "Feishu tablet identity active: " +
                    "brand=${android.os.Build.BRAND}, " +
                    "manufacturer=${android.os.Build.MANUFACTURER}, " +
                    "model=${android.os.Build.MODEL}, " +
                    "characteristics=$reportedCharacteristics"
            )

            afterApplicationAttach(tag = TAG) { context ->
                val appClassLoader = context.classLoader
                val deviceModelMethods =
                    "com.ss.android.lark.passport.signinsdk_api.entity.DeviceInfo"
                        .toClass(appClassLoader)
                        .resolve()
                        .method {
                            name("getDeviceModel")
                            returnType(String::class)
                        }.map { it.self }

                check(deviceModelMethods.size == 1) {
                    "Expected exactly one Feishu DeviceInfo.getDeviceModel, " +
                        "found ${deviceModelMethods.size}"
                }
                val loggedFirstDeviceModelRead = AtomicBoolean(false)
                deviceModelMethods.forEach { method ->
                    hook(method).intercept {
                        if (loggedFirstDeviceModelRead.compareAndSet(false, true)) {
                            log(
                                Log.INFO,
                                TAG,
                                "Feishu read hooked DeviceInfo model: ${FeishuTabletProfile.model}"
                            )
                        }
                        FeishuTabletProfile.model
                    }
                }
                val romModel = "com.larksuite.framework.utils.RomUtils"
                    .toClass(appClassLoader)
                    .getDeclaredMethod("d")
                    .invoke(null)
                log(
                    Log.INFO,
                    TAG,
                    "Installed Feishu tablet hooks: " +
                        "deviceModel=${deviceModelMethods.size}, " +
                        "brand=${FeishuTabletProfile.brand}, " +
                        "model=${FeishuTabletProfile.model}, " +
                        "romModel=$romModel"
                )
            }
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to install Feishu hooks: ${it.stackTraceToString()}")
        }
    }

    private fun hookDexMethodToReturn(
        cacheKey: String,
        context: Context,
        value: Any?,
        finder: DexKitBridge.() -> DexMethod
    ) {
        val classLoader = context.classLoader
        val method = requireMethodCache(context).findOrLoad(cacheKey, classLoader, finder)
        val hit = AtomicBoolean(false)
        hook(method).intercept {
            if (hit.compareAndSet(false, true)) {
                log(Log.INFO, TAG, "[IAmPad-startup] hook-hit=$cacheKey")
            }
            value
        }
        log(Log.INFO, TAG, "[IAmPad-startup] hook-installed=$cacheKey")
    }

    private fun requireMethodCache(context: Context): DexMethodCache {
        methodCache?.let { return it }
        return DexMethodCache(
            module = this,
            prefs = context.getSharedPreferences(DEXKIT_PREFS_NAME, Context.MODE_PRIVATE)
        ).also { methodCache = it }
    }

    private fun isCustomWeWork(prp: XposedModuleInterface.PackageReadyParam): Boolean = prp.run {
        packageName in customWeWorkPackages || classLoader.hasClass("com.tencent.wework.common.utils.WwUtil")
    }

    private fun isDingTalk(prp: XposedModuleInterface.PackageReadyParam): Boolean = prp.run {
        packageName == "com.alibaba.android.rimet" || classLoader.hasClass("com.alibaba.android.rimet.LauncherApplication")
    }
}
