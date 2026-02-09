package org.gibil

import okhttp3.OkHttpClient
import org.gibil.service.ApiService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import routes.api.AvinorApiHandler
import routes.api.AvinorXmlFeedParams
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            val code = url.substringAfterLast("/")
            return "<valid_xml_for_$code>"
        }
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with all valid parameters returns URL`() {
        val result = apiHandler.avinorXmlFeedUrlBuilder(
            AvinorXmlFeedParams(
                airportCode = "OSL",
                timeFrom = 1,
                timeTo = 7,
                direction = "D",
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
