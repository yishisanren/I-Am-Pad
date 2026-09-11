package com.houvven.impad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageTargetResolverTest {

    @Test
    fun `routes the official Feishu package`() {
        assertEquals(PackageTarget.FEISHU, PackageTargetResolver.resolve("com.ss.android.lark"))
    }

    @Test
    fun `does not route lookalike or international Lark packages`() {
        assertNull(PackageTargetResolver.resolve("com.ss.android.lark.debug"))
        assertNull(PackageTargetResolver.resolve("com.larksuite.suite"))
    }
}
