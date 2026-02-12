package org.gibil

import okhttp3.OkHttpClient
import org.gibil.service.ApiService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import routes.api.AvinorApiHandler
import model.AvinorXmlFeedParams
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URI

class AvinorApiHandlerTest() {
    val spyApiService = SpyApiService()
    val apiHandler = AvinorApiHandler(spyApiService)
    val clock: Clock = Clock.systemUTC()

    /**
     * A fake implementation of ApiService that returns controlled responses
     * instead of making real HTTP calls.
     */
    class SpyApiService : ApiService(OkHttpClient()) {
        var simulateError = false

        override fun apiCall(url: String, acceptHeader: String?): String? {
            if (simulateError) {
                return "Error: 500 Server Error"
            }

            // Extract airport code from query parameter
            val airportCode = url.substringAfter("airport=", "").substringBefore("&")

            // Return appropriate response based on which API is being called
            return when {
                url.contains("airportNames") -> {
                    // Simulate airport names API response format
                    """<airportNames><airportName code="$airportCode" name="Airport"/></airportNames>"""
                }
                else -> {
                    // Default response for other APIs
                    "<valid_xml_for_$airportCode>"
                }
            }
        }
    }

    fun `avinorXmlFeedUrlBuilder constructs correct URL with all parameters`() {
        // Arrange
        val params = AvinorXmlFeedParams(
            airportCode = "OSL",
            timeFrom = 1,
            timeTo = 7,
            direction = "D"
        )

        // Act
        val resultUrl = apiHandler.avinorXmlFeedUrlBuilder(params)

        // Assert
        assertNotNull(resultUrl)

        // Verify it is a valid URI
        assertDoesNotThrow { URI.create(resultUrl) }

        // Verify query parameters exist (order might vary, so checking containment is safer)
        assertTrue(resultUrl.contains("airport=OSL"), "URL should contain airport param")
        assertTrue(resultUrl.contains("TimeFrom=1"), "URL should contain TimeFrom param")
        assertTrue(resultUrl.contains("TimeTo=7"), "URL should contain TimeTo param")
        assertTrue(resultUrl.contains("direction=D"), "URL should contain direction param")
    }

    @Test
    fun `avinorXmlFeedUrlBuilder excludes direction when not provided`() {
        // Arrange (Assuming params allows null/empty direction or you pass a neutral one)
        // If your new Model enforces direction logic, use a case where direction is ignored or optional.
        // Based on your code, it only adds param if direction is "A" or "D".
        // Let's pass a param that SHOULD be ignored if logic allows, or create a specific case.

        val params = AvinorXmlFeedParams(
            airportCode = "OSL",
            timeFrom = 1,
            timeTo = 7,
            direction = "" // Empty direction
        )

        // Act
        val resultUrl = apiHandler.avinorXmlFeedUrlBuilder(params)

        // Assert
        assertFalse(resultUrl.contains("direction="), "URL should NOT contain direction param for empty input")
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with all valid parameters returns URL`() {
        val result = apiHandler.avinorXmlFeedUrlBuilder(
            AvinorXmlFeedParams(
                airportCode = "OSL",
                timeFrom = 1,
                timeTo = 7,
                direction = "D"
            )
        )

        requireNotNull(result) { "url builder returned null" }
        assertTrue(result.contains("airport=OSL"))
        assertTrue(result.contains("direction"))
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with invalid airport code throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            apiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParams(
                    airportCode = "OS",
                    timeFrom = 1,
                    timeTo = 7,
                    direction = "D",
                )
            )
        }
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with negative time from throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            apiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParams(
                    airportCode = "OSL",
                    timeFrom = -100,
                    timeTo = 7,
                    direction = "D",
                )
            )
        }
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with time exceeding limit throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            apiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParams(
                    airportCode = "OSL",
                    timeFrom = 1,
                    timeTo = 700000000,
                    direction = "D",
                )
            )
        }
    }

    @Test
    fun `userCorrectDate with valid iso timestamp returns localized date string`() {
        val timeNow: Instant = Instant.now(clock)

        val datetimeUserCorrect = timeNow.atZone(ZoneId.systemDefault())

        var datetimeUserDifferentZone = timeNow.atZone(ZoneId.of("America/Los_Angeles"))

        //if the users timezone is conicidentally america/LA, set something different
        if (datetimeUserDifferentZone == datetimeUserCorrect) {
            datetimeUserDifferentZone = timeNow.atZone(ZoneId.of("Europe/Paris"))
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val displayTime = datetimeUserCorrect.format(formatter)
        val displayTimeWrong = datetimeUserDifferentZone.format(formatter)

        val result = apiHandler.userCorrectDate(datetimeUserDifferentZone.toString())

        assertEquals(displayTime, result)
    }

    @Test
    fun `userCorrectDate with invalid date format returns error`() {
        val datetime = "not a valid date format"
        val result = apiHandler.userCorrectDate(datetime)

        assertTrue(result.contains("Error: Date format in"))
    }
}
