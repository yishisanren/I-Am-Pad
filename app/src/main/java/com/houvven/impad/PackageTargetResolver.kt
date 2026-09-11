package com.houvven.impad

internal enum class PackageTarget {
    FEISHU
}

internal object PackageTargetResolver {
    private const val FEISHU_PACKAGE = "com.ss.android.lark"

    fun resolve(packageName: String): PackageTarget? = when (packageName) {
        FEISHU_PACKAGE -> PackageTarget.FEISHU
        else -> null
    }
}
