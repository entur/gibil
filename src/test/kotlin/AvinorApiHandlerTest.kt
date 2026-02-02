package org.example

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import routes.api.AvinorApiHandler
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class AvinorApiHandlerTest {

    private val clock = Clock.systemUTC()

    val api = AvinorApiHandler(clock)

    @Test
    fun `avinorXmlFeedApiCall with all valid parameters returns XML with airport data`() {
        /*
        A test where every parameter is set and is valid
         */
        val result = api.avinorXmlFeedApiCall(
            airportCodeParam = "OSL",
            timeFromParam = 1,
            timeToParam = 7,
            directionParam = "D",
            lastUpdateParam = Instant.parse("2024-08-08T09:30:00Z"),
            includeHelicopterParam = true
        )
        requireNotNull(result) {"api call returned null"}
        assertTrue(result.contains("<airport"))
        assertTrue(result.contains("name=\"OSL\""))
        println(result)
    }

    @Test
    fun `avinorXmlFeedApiCall with invalid airport code throws exception`() {
        /*
        A test where the aiport-code is not a valid airport code
         */
        assertThrows(IllegalArgumentException::class.java) {
            val result = api.avinorXmlFeedApiCall(
                airportCodeParam = "OS",
                timeFromParam = 1,
                timeToParam = 7,
                directionParam = "D",
                lastUpdateParam = Instant.parse("2024-08-08T09:30:00Z"),
                includeHelicopterParam = true
            )
        }
    }

    @Test
    fun `avinorXmlFeedApiCall with invalid direction throws exception`() {
        /*
        A test where the aiport-code is not a valid airport code
         */
        assertThrows(IllegalArgumentException::class.java) {
            val result = api.avinorXmlFeedApiCall(
                airportCodeParam = "OSL",
                timeFromParam = 1,
                timeToParam = 7,
                directionParam = "f",
                lastUpdateParam = Instant.parse("2024-08-08T09:30:00Z"),
                includeHelicopterParam = true
            )
        }
    }

    @Test
    fun `avinorXmlFeedApiCall with negative time from throws exception`(){

        assertThrows(IllegalArgumentException::class.java) {
            val result = api.avinorXmlFeedApiCall(
                airportCodeParam = "OS",
                timeFromParam = -100,
                timeToParam = 7,
                directionParam = "D",
                lastUpdateParam = Instant.parse("2024-08-08T09:30:00Z"),
                includeHelicopterParam = true
            )
        }
    }

    @Test
    fun `avinorXmlFeedApiCall with time exceeding limit throw exception`() {

        assertThrows(IllegalArgumentException::class.java) {
            val result = api.avinorXmlFeedApiCall(
                airportCodeParam = "OS",
                timeFromParam = 1,
                timeToParam = 700000000,
                directionParam = "D",
                lastUpdateParam = Instant.parse("2024-08-08T09:30:00Z"),
                includeHelicopterParam = true
            )
        }
    }

    @Test
    fun `userCorrectDate with valid iso timestamp returns localized date string`() {
        //current time
        val timeNow: Instant = Instant.now(clock)

        //Makes a time which is correct to the users default timezone
        val datetimeUserCorrect = timeNow.atZone(ZoneId.systemDefault())

        //makes a time which is of LA time, probably different but works for the test anyway
        var datetimeUserDifferentZone = timeNow.atZone(ZoneId.of("America/Los_Angeles"))

        //if the users timezone is conicidentally america/LA, set something different
        if (datetimeUserDifferentZone == datetimeUserCorrect){
            datetimeUserDifferentZone = timeNow.atZone(ZoneId.of("Europe/Paris"))
        }

        //format for output
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val displayTime = datetimeUserCorrect.format(formatter)
        val displayTimeWrong = datetimeUserDifferentZone.format(formatter)

        // Pass the Instant string, not the ZonedDateTime string
        val result = api.userCorrectDate(datetimeUserDifferentZone.toString())

        println("Result: $result")
        println("Expected: $displayTime")
        println("Wrong timezone example: $displayTimeWrong")

        assertEquals(displayTime, result)
    }

    @Test
    fun `userCorrectDate with invalid date format returns error`(){
        val datetime = "not a valid date format"
        val result = api.userCorrectDate(datetime)
        println(result)
        assertTrue(result.contains("Error: Date format in"))
    }
}