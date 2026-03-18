package siri

import model.FlightStop
import model.UnifiedFlight
import org.gibil.service.AirportQuayService
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import uk.org.siri.siri21.CallStatusEnumeration

class SiriETMapperTest {

    private val airportQuayService = mockk<AirportQuayService> {
        every { getQuayId(any()) } answers { "NSR:Quay:${firstArg<String>()}" }
    }
    private val mapper = SiriETMapper(airportQuayService)

    @Test
    fun `should handle empty flight list`() {
        val result = mapper.mapUnifiedFlightsToSiri(emptyList())

        assertTrue(getJourneys(result).isEmpty())
    }

    @ParameterizedTest
    @CsvSource(
        "OSL,BGO,NSR:Quay:OSL,NSR:Quay:BGO",
        "BGO,OSL,NSR:Quay:BGO,NSR:Quay:OSL"
    )
    fun `should create correct journey structure`(
        origin: String,
        destination: String,
        expectedFirstStop: String,
        expectedSecondStop: String
    ) {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(origin = origin, destination = destination)))
        val journey = getJourneys(result)[0]
        val calls = journey.estimatedCalls.estimatedCalls

        assertEquals("0", journey.directionRef.value)
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
    fun `should skip flight when no serviceJourneyRef is set`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(serviceJourneyRef = null)))

        assertTrue(getJourneys(result).isEmpty())
    }

    @Test
    fun `should skip flight when no lineRef is set`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(lineRef = null)))

        assertTrue(getJourneys(result).isEmpty())
    }

    @Test
    fun `should drop flight when any stop has no quay`() {
        every { airportQuayService.getQuayId("BGO") } returns null

        val result = mapper.mapUnifiedFlightsToSiri(listOf(createFlight(origin = "OSL", destination = "BGO")))

        assertTrue(getJourneys(result).isEmpty())
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
        val scheduledTime = Instant.now()
        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(departureTime = scheduledTime, departureStatusCode = "E", departureStatusTime = scheduledTime)
        ))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[0]

        assertEquals(CallStatusEnumeration.ON_TIME, call.departureStatus)
        assertNotNull(call.expectedDepartureTime)
    }

    @Test
    fun `should set departure status to DELAYED when new time differs from scheduled`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(departureStatusCode = "E", departureStatusTime = Instant.now().plus(30, ChronoUnit.MINUTES))
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
            createFlight(arrivalStatusCode = "E", arrivalStatusTime = Instant.now().plus(30, ChronoUnit.MINUTES))
        ))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[1]

        assertEquals(CallStatusEnumeration.EARLY, call.arrivalStatus)
        assertNotNull(call.expectedArrivalTime)
    }

    @Test
    fun `should set arrival status to ON_TIME when new time matches scheduled`() {
        val scheduledTime = Instant.now().plus(1, ChronoUnit.HOURS)
        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(arrivalTime = scheduledTime, arrivalStatusCode = "E", arrivalStatusTime = scheduledTime)
        ))
        val call = getJourneys(result)[0].estimatedCalls.estimatedCalls[1]

        assertEquals(CallStatusEnumeration.ON_TIME, call.arrivalStatus)
        assertNotNull(call.expectedArrivalTime)
    }

    @Test
    fun `should set arrival status to DELAYED when new time is after scheduled`() {
        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(arrivalStatusCode = "E", arrivalStatusTime = Instant.now().plus(2, ChronoUnit.HOURS))
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
    fun `should adjust downstream arrival when previous departure is later`() {
        val firstScheduledDeparture = Instant.parse("2026-03-17T12:00:00Z")
        val firstExpectedDeparture = Instant.parse("2026-03-17T13:00:00Z")
        val secondScheduledArrival = Instant.parse("2026-03-17T12:50:00Z")

        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createFlight(
                departureTime = firstScheduledDeparture,
                departureStatusCode = "E",
                departureStatusTime = firstExpectedDeparture,
                arrivalTime = secondScheduledArrival
            )
        ))

        val calls = getJourneys(result)[0].estimatedCalls.estimatedCalls
        assertEquals(secondScheduledArrival.plus(1, ChronoUnit.HOURS).atZone(ZoneOffset.UTC), calls[1].expectedArrivalTime)
        assertFalse(calls[1].expectedArrivalTime.isBefore(calls[0].expectedDepartureTime))
        assertEquals(CallStatusEnumeration.DELAYED, calls[1].arrivalStatus)
    }

    @Test
    fun `should adjust intermediate departure when updated arrival is later`() {
        val secondArrivalScheduled = Instant.parse("2026-03-17T13:00:00Z")
        val secondArrivalUpdated = Instant.parse("2026-03-17T13:30:00Z")
        val secondDepartureScheduled = Instant.parse("2026-03-17T13:10:00Z")

        val result = mapper.mapUnifiedFlightsToSiri(listOf(
            createMultiLegFlight(
                secondArrivalTime = secondArrivalScheduled,
                secondArrivalStatusTime = secondArrivalUpdated,
                secondDepartureTime = secondDepartureScheduled
            )
        ))

        val secondCall = getJourneys(result)[0].estimatedCalls.estimatedCalls[1]
        assertEquals(secondDepartureScheduled.plus(30, ChronoUnit.MINUTES).atZone(ZoneOffset.UTC), secondCall.expectedDepartureTime)
        assertFalse(secondCall.expectedDepartureTime.isBefore(secondCall.expectedArrivalTime))
        assertEquals(CallStatusEnumeration.DELAYED, secondCall.departureStatus)
    }

    private fun getJourneys(result: uk.org.siri.siri21.Siri) =
        result.serviceDelivery.estimatedTimetableDeliveries[0]
            .estimatedJourneyVersionFrames[0].estimatedVehicleJourneies

    private fun createFlight(
        flightId: String = "SK123",
        operator: String = "SK",
        origin: String = "OSL",
        destination: String = "BGO",
        departureTime: Instant = Instant.now(),
        departureStatusCode: String? = null,
        departureStatusTime: Instant? = null,
        arrivalTime: Instant = Instant.now().plus(1, ChronoUnit.HOURS),
        arrivalStatusCode: String? = null,
        arrivalStatusTime: Instant? = null,
        serviceJourneyRef: String? = "AVI:ServiceJourney:SK123_hash",
        lineRef: String? = "AVI:LineRef:SK_OSL-BGO"
    ) = UnifiedFlight(
        flightId = flightId,
        operator = operator,
        date = LocalDate.now(),
        serviceJourneyRef = serviceJourneyRef,
        lineRef = lineRef,
        stops = listOf(
            FlightStop(
                airportCode = origin,
                arrivalTime = null,
                departureTime = departureTime,
                departureStatusCode = departureStatusCode,
                departureStatusTime = departureStatusTime
            ),
            FlightStop(
                airportCode = destination,
                arrivalTime = arrivalTime,
                departureTime = null,
                arrivalStatusCode = arrivalStatusCode,
                arrivalStatusTime = arrivalStatusTime
            )
        )
    )

    private fun createMultiLegFlight(
        secondArrivalTime: Instant,
        secondArrivalStatusTime: Instant,
        secondDepartureTime: Instant
    ) = UnifiedFlight(
        flightId = "DX571",
        operator = "DX",
        date = LocalDate.now(),
        serviceJourneyRef = "AVI:ServiceJourney:DX571-01-1860721267",
        lineRef = "AVI:Line:DX_OSL-FRO",
        stops = listOf(
            FlightStop(
                airportCode = "OSL",
                arrivalTime = null,
                departureTime = Instant.parse("2026-03-17T12:00:00Z")
            ),
            FlightStop(
                airportCode = "1173",
                arrivalTime = secondArrivalTime,
                departureTime = secondDepartureTime,
                arrivalStatusCode = "E",
                arrivalStatusTime = secondArrivalStatusTime
            ),
            FlightStop(
                airportCode = "FRO",
                arrivalTime = Instant.parse("2026-03-17T14:20:00Z"),
                departureTime = null
            )
        )
    )
}
