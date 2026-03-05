package service

import io.mockk.every
import io.mockk.mockk
import model.FlightStop
import model.UnifiedFlight
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ServiceJourneyResolverTest {

    private val findServiceJourneyService = mockk<FindServiceJourneyService>()
    private val resolver = ServiceJourneyResolver(findServiceJourneyService)

    @Test
    fun `should attach ref when match is found`() {
        every { findServiceJourneyService.matchServiceJourney(any(), any()) } returns "AVI:ServiceJourney:SK123_hash"

        val result = resolver.resolve(listOf(createFlight("SK123")))

        assertEquals("AVI:ServiceJourney:SK123_hash", result[0].serviceJourneyRef)
    }

    @Test
    fun `should return flight with null ref when no match is found`() {
        every { findServiceJourneyService.matchServiceJourney(any(), any()) } throws ServiceJourneyNotFoundException("no match")

        val result = resolver.resolve(listOf(createFlight()))

        assertNull(result[0].serviceJourneyRef)
    }

    @Test
    fun `should return flight with null ref when lookup throws unexpected exception`() {
        every { findServiceJourneyService.matchServiceJourney(any(), any()) } throws RuntimeException("ExTime unavailable")

        val result = resolver.resolve(listOf(createFlight()))

        assertNull(result[0].serviceJourneyRef)
    }

    @Test
    fun `should return flight with null ref when first stop has no departure time`() {
        val flight = createFlight(departureTime = null)

        val result = resolver.resolve(listOf(flight))

        assertNull(result[0].serviceJourneyRef)
    }

    @Test
    fun `should process all flights independently when one fails`() {
        every { findServiceJourneyService.matchServiceJourney(any(), "SK123") } throws ServiceJourneyNotFoundException("no match")
        every { findServiceJourneyService.matchServiceJourney(any(), "DY456") } returns "AVI:ServiceJourney:DY456_hash"

        val result = resolver.resolve(listOf(createFlight("SK123"), createFlight("DY456")))

        assertNull(result[0].serviceJourneyRef)
        assertEquals("AVI:ServiceJourney:DY456_hash", result[1].serviceJourneyRef)
    }

    @Test
    fun `should return full list regardless of match failures`() {
        every { findServiceJourneyService.matchServiceJourney(any(), any()) } throws ServiceJourneyNotFoundException("no match")

        val result = resolver.resolve(listOf(createFlight("SK123"), createFlight("DY456"), createFlight("WF789")))

        assertEquals(3, result.size)
    }

    private fun createFlight(
        flightId: String = "SK123",
        departureTime: Instant? = Instant.now()
    ) = UnifiedFlight(
        flightId = flightId,
        operator = flightId.take(2),
        date = LocalDate.now(),
        stops = listOf(
            FlightStop(airportCode = "OSL", arrivalTime = null, departureTime = departureTime),
            FlightStop(airportCode = "BGO", arrivalTime = Instant.now().plus(1, ChronoUnit.HOURS), departureTime = null)
        )
    )
}
