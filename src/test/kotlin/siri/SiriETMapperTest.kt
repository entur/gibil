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
import uk.org.siri.siri21.CallStatusEnumeration

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
    fun `should set departure status to MISSED when flight has departed`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(departureStatusCode = "D")))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[0]

        assertEquals(CallStatusEnumeration.MISSED, call.departureStatus)
        assertNotNull(call.expectedDepartureTime)
    }

    @Test
    fun `should set departure status to ON_TIME when new time matches scheduled`() {
        val scheduledTime = LocalDateTime.now()
        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(departureStatusCode = "E", departureStatusTime = scheduledTime)
        ))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[0]

        assertEquals(CallStatusEnumeration.ON_TIME, call.departureStatus)
        assertNotNull(call.expectedDepartureTime)
    }

    @Test
    fun `should set departure status to DELAYED when new time differs from scheduled`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(departureStatusCode = "E", departureStatusTime = LocalDateTime.now().plusMinutes(30))
        ))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[0]

        assertEquals(CallStatusEnumeration.DELAYED, call.departureStatus)
        assertNotNull(call.expectedDepartureTime)
    }

    @Test
    fun `should set departure status to CANCELLED when flight is cancelled`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(departureStatusCode = "C")))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[0]

        assertEquals(CallStatusEnumeration.CANCELLED, call.departureStatus)
        assertTrue(call.isCancellation)
    }

    @Test
    fun `should set arrival status to ARRIVED when flight has arrived`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(arrivalStatusCode = "A")))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[1]

        assertEquals(CallStatusEnumeration.ARRIVED, call.arrivalStatus)
        assertNotNull(call.expectedArrivalTime)
    }

    @Test
    fun `should set arrival status to EARLY when new time is before scheduled`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(arrivalStatusCode = "E", arrivalStatusTime = LocalDateTime.now().plusMinutes(30))
        ))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[1]

        assertEquals(CallStatusEnumeration.EARLY, call.arrivalStatus)
        assertNotNull(call.expectedArrivalTime)
    }

    @Test
    fun `should set arrival status to ON_TIME when new time matches scheduled`() {
        val scheduledTime = LocalDateTime.now().plusHours(1)
        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(arrivalStatusCode = "E", arrivalStatusTime = scheduledTime)
        ))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[1]

        assertEquals(CallStatusEnumeration.ON_TIME, call.arrivalStatus)
        assertNotNull(call.expectedArrivalTime)
    }

    @Test
    fun `should set arrival status to DELAYED when new time is after scheduled`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(arrivalStatusCode = "E", arrivalStatusTime = LocalDateTime.now().plusHours(2))
        ))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[1]

        assertEquals(CallStatusEnumeration.DELAYED, call.arrivalStatus)
        assertNotNull(call.expectedArrivalTime)
    }

    @Test
    fun `should set arrival status to CANCELLED when flight is cancelled`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(arrivalStatusCode = "C")))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[1]

        assertEquals(CallStatusEnumeration.CANCELLED, call.arrivalStatus)
        assertTrue(call.isCancellation)
    }

    @Test
    fun `should skip flight when service journey lookup throws an exception`() {
        every { findServiceJourneyService.matchServiceJourney(any(), any()) } throws RuntimeException("ExTime unavailable")
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight()))

        assertTrue(getJourneys(result).isEmpty())
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
        departureStatusTime: LocalDateTime? = null,
        arrivalStatusCode: String? = null,
        arrivalStatusTime: LocalDateTime? = null
    ) = UnifiedFlight(
        flightId = flightId,
        operator = operator,
        date = LocalDate.now(),
        stops = listOf(
            FlightStop(
                airportCode = origin,
                arrivalTime = null,
                departureTime = LocalDateTime.now(),
                departureStatusCode = departureStatusCode,
                departureStatusTime = departureStatusTime
            ),
            FlightStop(
                airportCode = destination,
                arrivalTime = LocalDateTime.now().plusHours(1),
                departureTime = null,
                arrivalStatusCode = arrivalStatusCode,
                arrivalStatusTime = arrivalStatusTime
            )
        )
    )
}
