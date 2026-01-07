package org.example

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UrlBuilderTest {

    @Test
    fun testBasicUrl() {
        val result = urlBuilder("OSL")
        assertNotNull(result)
        assertTrue(result.contains("airport=OSL"))
    }
}