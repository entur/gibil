package org.gibil

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import routes.api.AvinorApiHandler
import routes.api.AvinorXmlFeedParams
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AvinorApiHandlerTest {
    val api = AvinorApiHandler()
    val clock: Clock = Clock.systemUTC()

    @Test
    fun `avinorXmlFeedUrlBuilder with all valid parameters returns URL`() {
        val result = api.avinorXmlFeedUrlBuilder(
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
        println(result)
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with invalid airport code throws exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            api.avinorXmlFeedUrlBuilder(
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
            api.avinorXmlFeedUrlBuilder(
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
            api.avinorXmlFeedUrlBuilder(
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

        val result = api.userCorrectDate(datetimeUserDifferentZone.toString())

        println("Result: $result")
        println("Expected: $displayTime")
        println("Wrong timezone example: $displayTimeWrong")

        assertEquals(displayTime, result)
    }

    @Test
    fun `userCorrectDate with invalid date format returns error`() {
        val datetime = "not a valid date format"
        val result = api.userCorrectDate(datetime)
        println(result)
        assertTrue(result.contains("Error: Date format in"))
    }
}
