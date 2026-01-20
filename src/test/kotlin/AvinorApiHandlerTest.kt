package org.example

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import routes.api.AvinorApiHandler
import java.time.Instant
import routes.api.AvinorApiHandler

class AvinorApiHandlerTest {
    val api = AvinorApiHandler()

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
                airportCodeParam = "OS",
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
    fun `userCorrectDate with valid iso timestamp returns localized date string`(){
        val datetime = "2024-08-08T09:30:00Z"
        val result = api.userCorrectDate(datetime)
        println(result)
        assertTrue(result.contains("2024-08-08 11:30:00"))

    }

    @Test
    fun `userCorrectDate with invalid date format returns error`(){
        val datetime = "not a valid date format"
        val result = api.userCorrectDate(datetime)
        println(result)
        assertTrue(result.contains("Error: Date format invalid;"))
    }
}