package siri

import model.FlightStop
import model.UnifiedFlight
import org.gibil.service.AirportQuayService
import io.mockk.*
import service.FindServiceJourneyService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalDateTime

class SiriETMapperTest {

    private val airportQuayService = mockk<AirportQuayService> {
        every { getQuayId(any()) } returns null
    }
    private val findServiceJourneyService = mockk<FindServiceJourneyService> {
        // Return a VJR string that contains the flightId so the VJR check passes
        every { matchServiceJourney(any(), any()) } answers { "AVI:ServiceJourney:${secondArg<String>()}_hash" }
    }
    private val mapper = SiriETMapper(airportQuayService, findServiceJourneyService)

    @Test
    fun `should handle empty flight list`() {
        val result = mapper.mapUnifiedFlightsToSiri(emptyList())

        assertTrue(getJourneys(result).isEmpty())
    }

    @ParameterizedTest
    @CsvSource(
        "OSL,BGO,outbound,AVI:StopPointRef:OSL,AVI:StopPointRef:BGO",
        "BGO,OSL,inbound,AVI:StopPointRef:BGO,AVI:StopPointRef:OSL"
    )
    fun `should create correct journey structure`(
        origin: String,
        destination: String,
        expectedDirection: String,
        expectedFirstStop: String,
        expectedSecondStop: String
    ) {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(origin = origin, destination = destination)))
        val journey = getJourneys(result)[0]
        val calls = journey.estimatedCalls.estimatedCalls

        assertEquals(expectedDirection, journey.directionRef.value)
        assertEquals(2, calls.size)
        assertEquals(expectedFirstStop, calls[0].stopPointRef.value)
        assertEquals(expectedSecondStop, calls[1].stopPointRef.value)
    }

    @Test
    fun `should map operator correctly`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(operator = "SK")))

        assertEquals("AVI:Operator:SK", getJourneys(result)[0].operatorRef.value)
    }

    @Test
    fun `should handle multiple flights`() {
        val flights = listOf(
            createFlight("SK123", "SK"),
            createFlight("DY456", "DY"),
            createFlight("WF789", "WF")
        )
        val result = mapper.mapUnifiedFlightsToSiri(flights)

        assertEquals(3, getJourneys(result).size)
    }

    @Test
    fun `should set aimed departure and arrival times`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight()))
        val calls = getJourneys(result)[0].estimatedCalls.estimatedCalls

        assertNotNull(calls[0].aimedDepartureTime)
        assertNotNull(calls[1].aimedArrivalTime)
    }

    @Test
    fun `should skip flight when no service journey match is found`() {
        every { findServiceJourneyService.matchServiceJourney(any(), any()) } returns ""
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight()))

        assertTrue(getJourneys(result).isEmpty())
    }

    private fun getJourneys(result: uk.org.siri.siri21.Siri) =
        result.serviceDelivery.estimatedTimetableDeliveries[0]
            .estimatedJourneyVersionFrames[0].estimatedVehicleJourneies

    private fun createFlight(
        flightId: String = "SK123",
        operator: String = "SK",
        origin: String = "OSL",
        destination: String = "BGO",
        departureStatusCode: String? = null,
        arrivalStatusCode: String? = null
    ) = UnifiedFlight(
        flightId = flightId,
        operator = operator,
        date = LocalDate.now(),
        stops = listOf(
            FlightStop(
                airportCode = origin,
                arrivalTime = null,
                departureTime = LocalDateTime.now(),
                departureStatusCode = departureStatusCode
            ),
            FlightStop(
                airportCode = destination,
                arrivalTime = LocalDateTime.now().plusHours(1),
                departureTime = null,
                arrivalStatusCode = arrivalStatusCode
            )
        )
    )
}
