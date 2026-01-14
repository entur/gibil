package org.example

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class AvinorApiHandlingTest {
    val api = AvinorApiHandling()

    @Test
    fun apiCallTest() {
        /*
        A test where every parameter is set and is valid
         */
        val result = api.avinorXmlFeedApiCall(
            airportCodeParam = "OSL",
            timeFromParam = 1,
            timeToParam = 7,
            directionParam = "D",
            lastUpdateParam = Instant.parse("2024-08-08T09:30:00Z"),
            serviceTypeParam = "E"
        )
        requireNotNull(result) {"api call returned null"}
        assertTrue(result.contains("<airport"))
        assertTrue(result.contains("name=\"OSL\""))
        println(result)
    }

    @Test
    fun apiCallBadAirportCodeTest(){
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
                serviceTypeParam = "E"
            )
        }
    }

    @Test
    fun apiCallBadDirectionCodeTest(){
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
                serviceTypeParam = "E"
            )
        }
    }

    @Test
    fun apiCallBadTimeFromCodeTest(){
        /*
        A test where the aiport-code is not a valid airport code
         */
        assertThrows(IllegalArgumentException::class.java) {
            val result = api.avinorXmlFeedApiCall(
                airportCodeParam = "OS",
                timeFromParam = -100,
                timeToParam = 7,
                directionParam = "D",
                lastUpdateParam = Instant.parse("2024-08-08T09:30:00Z"),
                serviceTypeParam = "E"
            )
        }
    }

    @Test
    fun apiCallBadTimeToTest(){
        /*
        A test where the aiport-code is not a valid airport code
         */
        assertThrows(IllegalArgumentException::class.java) {
            val result = api.avinorXmlFeedApiCall(
                airportCodeParam = "OS",
                timeFromParam = 1,
                timeToParam = 700000000,
                directionParam = "D",
                lastUpdateParam = Instant.parse("2024-08-08T09:30:00Z"),
                serviceTypeParam = "E"
            )
        }
    }

    @Test
    fun apiCallBadServiceTypeTest(){
        /*
        A test where the aiport-code is not a valid airport code
         */
        assertThrows(IllegalArgumentException::class.java) {
            val result = api.avinorXmlFeedApiCall(
                airportCodeParam = "OS",
                timeFromParam = 1,
                timeToParam = 700000000,
                directionParam = "D",
                lastUpdateParam = Instant.parse("2024-08-08T09:30:00Z"),
                serviceTypeParam = "aisdnaisdnaidn"
            )
        }
    }
}