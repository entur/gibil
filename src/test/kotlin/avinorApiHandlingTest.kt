package org.example

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AvinorApiHandlingTest {

    @Test
    fun apiCallTest() {
        val api = AvinorApiHandling()
        val result = api.apiCall(
            airportCodeParam = "OSL"
        )
        requireNotNull(result) {"api call returned null"}
        assertTrue(result.contains("<airport"))
        assertTrue(result.contains("name=\"OSL\""))
        println(result)
    }
}