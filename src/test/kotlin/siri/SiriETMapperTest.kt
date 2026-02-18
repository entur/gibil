package siri

import model.xmlFeedApi.Airport
import model.xmlFeedApi.Flight
import model.xmlFeedApi.FlightsContainer
import model.xmlFeedApi.FlightStatus
import okhttp3.OkHttpClient
import org.gibil.StopPlaceMapper
import org.gibil.routes.api.StopPlaceApiHandler
import org.gibil.service.AirportQuayService
import org.gibil.service.ApiService
import io.mockk.mockk
import service.FindServiceJourney
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.ZonedDateTime

class SiriETMapperTest() {

    class SpyAirportQuayService : AirportQuayService(
        StopPlaceApiHandler(ApiService(OkHttpClient())),
        StopPlaceMapper()
    ) {
        override fun getQuayId(iataCode: String): String? = null
    }

    private val findServiceJourney = mockk<FindServiceJourney>(relaxed = true)
    private val mapper = SiriETMapper(SpyAirportQuayService(), findServiceJourney)


    @Test
    fun `should handle empty flight list`() {
        val airport = createAirport(flights = emptyList())
        val result = mapper.mapToSiri(airport, "OSL")

        assertTrue(getJourneys(result).isEmpty())
    }

    @ParameterizedTest
    @CsvSource(
        "null,SK,true",
        "SK123,null,true"
    )
    fun `should skip flights with missing required fields`(flightId: String?, airline: String?) {
        val flight = createFlight(
            flightId = if (flightId == "null") null else flightId,
            airline = if (airline == "null") null else airline
        )
        val airport = createAirport(flights = listOf(flight))
        val result = mapper.mapToSiri(airport, "OSL")

        assertTrue(getJourneys(result).isEmpty())
    }

    @ParameterizedTest
    @CsvSource(
        "true,outbound,AVI:StopPointRef:OSL,AVI:StopPointRef:BGO",
        "false,inbound,AVI:StopPointRef:BGO,AVI:StopPointRef:OSL"
    )
    fun `should create correct journey structure`(
        isDeparture: Boolean,
        expectedDirection: String,
        expectedFirstStop: String,
        expectedSecondStop: String
    ) {
        val airport = createAirport(flights = listOf(createFlight(isDeparture = isDeparture)))
        val result = mapper.mapToSiri(airport, "OSL")

        val journey = getJourneys(result)[0]
        val calls = journey.estimatedCalls.estimatedCalls

        assertEquals(expectedDirection, journey.directionRef.value)
        assertEquals(2, calls.size)
        assertEquals(expectedFirstStop, calls[0].stopPointRef.value)
        assertEquals(expectedSecondStop, calls[1].stopPointRef.value)
    }

    @Test
    fun `should map operator correctly`() {
        val airport = createAirport(flights = listOf(createFlight(airline = "SK")))
        val result = mapper.mapToSiri(airport, "OSL")

        assertEquals("AVI:Operator:SK", getJourneys(result)[0].operatorRef.value)
    }

    @Test
    fun `should handle multiple flights`() {
        val flights = listOf(
            createFlight("SK123", "SK", isDeparture = true),
            createFlight("DY456", "DY", isDeparture = true),
            createFlight("WF789", "WF", isDeparture = false)
        )
        val airport = createAirport(flights = flights)
        val result = mapper.mapToSiri(airport, "OSL")

        assertEquals(3, getJourneys(result).size)
    }

    @ParameterizedTest
    @CsvSource(
        "true,0",
        "false,1"
    )
    fun `should set aimed times correctly`(isDeparture: Boolean, callIndex: Int) {
        val airport = createAirport(flights = listOf(createFlight(isDeparture = isDeparture)))
        val result = mapper.mapToSiri(airport, "OSL")

        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[callIndex]

        if (isDeparture) {
            assertNotNull(call.aimedDepartureTime)
        } else {
            assertNotNull(call.aimedArrivalTime)
        }
    }

    private fun getJourneys(result: uk.org.siri.siri21.Siri) =
        result.serviceDelivery.estimatedTimetableDeliveries[0]
            .estimatedJourneyVersionFrames[0].estimatedVehicleJourneies

    private fun createAirport(flights: List<Flight>) = Airport().apply {
        flightsContainer = FlightsContainer().apply {
            lastUpdate = ZonedDateTime.now().toString()
            flight = flights.toMutableList()
        }
    }

    private fun createFlight(
        flightId: String? = "SK123",
        airline: String? = "SK",
        destinationAirport: String = "BGO",
        isDeparture: Boolean = true
    ) = Flight().apply {
        this.flightId = flightId
        uniqueID = flightId?.hashCode()?.toString() ?: "12345"
        this.airline = airline
        airport = destinationAirport
        scheduleTime = ZonedDateTime.now().toString()
        domInt = if (isDeparture) "D" else "I"
        arrDep = if (isDeparture) "D" else "A"
        status = FlightStatus().apply {
            code = "D"
            time = ZonedDateTime.now().toString()
        }
    }
}
