package com.houvven.impad

import org.junit.Assert.assertEquals
import org.junit.Test

class FeishuTabletProfileTest {

    @Test
    fun `uses a real tablet identity and tablet build characteristic`() {
        assertEquals("Xiaomi", FeishuTabletProfile.brand)
        assertEquals("23043RP34G", FeishuTabletProfile.model)
        assertEquals("tablet", FeishuTabletProfile.buildCharacteristics)
    }
}
