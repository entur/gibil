package org.example

import handler.AirlineNameHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class AirlineNameHandlerTest {
    val airlineNameHandler = AirlineNameHandler()

    @Test
    fun `GetName get a valid airline-code gets a full airline name`() {
        val result = airlineNameHandler.getName("AA")
        requireNotNull(result) {"api call returned null"}
        assertTrue(result.contains("American Airlines"))
    }

    @Test
    fun `GetName get an invalid airline-code gets a NA response`() {
        val result = airlineNameHandler.getName("adsmo")
        requireNotNull(result) {"api call returned null"}
        assertTrue(result.contains("Airlinename not found"))
    }

    @Test
    fun `IsValid get an a valid airline-code gets a valid`() {
        val result = airlineNameHandler.isValid("AA")
        assertTrue(result)
    }

    @Test
    fun `IsValid get an an invalid airline-code gets a valid`() {
        val result = airlineNameHandler.isValid("aosdiasoidmasoima")
        assertFalse(result)
    }

    @Test
    fun `Update creates airlines-json file and can run getName function`() {
        val testFile = "test-airlines.json"

        val cache = AirlineNameHandler(testFile)  // Use test file
        cache.update()

        assertEquals("American Airlines", cache.getName("AA"))

        // Cleanup
        File(testFile).delete()
    }
}