package service.serviceJourney

import io.mockk.every
import io.mockk.mockk
import model.FlightStop
import model.UnifiedFlight
import model.serviceJourney.LineRefWrapper
import model.serviceJourney.ServiceJourney
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ServiceJourneyResolverTest {

    private val findServiceJourney = mockk<FindServiceJourney>()
    private val resolver = ServiceJourneyResolver(findServiceJourney)

    @BeforeEach
    fun setup() {
        every { findServiceJourney.buildWorkingMap() } returns mutableMapOf()
    }

    @Test
    fun `should attach serviceJourneyRef when match is found`() {
        every {
            findServiceJourney.matchServiceJourney(
                any(),
                any(),
                any(),
                any()
            )
        } returns ServiceJourney(
            serviceJourneyId = "AVI:ServiceJourney:SK123_hash"
        )

        val result = resolver.resolve(listOf(createFlight("SK123")))

        Assertions.assertEquals("AVI:ServiceJourney:SK123_hash", result[0].serviceJourneyRef)
        Assertions.assertNull(result[0].lineRef)
    }

    @Test
    fun `should attach lineRef when match contains one`() {
        every {
            findServiceJourney.matchServiceJourney(
                any(),
                any(),
                any(),
                any()
            )
        } returns ServiceJourney(
            serviceJourneyId = "AVI:ServiceJourney:SK123_hash",
            lineRefElement = LineRefWrapper(ref = "AVI:LineRef:SK_OSL-BGO")
        )

        val result = resolver.resolve(listOf(createFlight("SK123")))

        Assertions.assertEquals("AVI:LineRef:SK_OSL-BGO", result[0].lineRef)
    }

    @Test
    fun `should return flight with null ref when no match is found`() {
        every {
            findServiceJourney.matchServiceJourney(
                any(),
                any(),
                any(),
                any()
            )
        } throws ServiceJourneyNotFoundException("no match")

        val result = resolver.resolve(listOf(createFlight()))

        Assertions.assertNull(result[0].serviceJourneyRef)
        Assertions.assertNull(result[0].lineRef)
    }

    @Test
    fun `should return flight with null ref when lookup throws unexpected exception`() {
        every {
            findServiceJourney.matchServiceJourney(
                any(),
                any(),
                any(),
                any()
            )
        } throws RuntimeException("ExTime unavailable")

        val result = resolver.resolve(listOf(createFlight()))

        Assertions.assertNull(result[0].serviceJourneyRef)
    }

    @Test
    fun `should return flight with null ref when first stop has no departure time`() {
        val flight = createFlight(departureTime = null)

        val result = resolver.resolve(listOf(flight))

        Assertions.assertNull(result[0].serviceJourneyRef)
    }

    @Test
    fun `should process all flights independently when one fails`() {
        every {
            findServiceJourney.matchServiceJourney(
                any(),
                any(),
                "SK123",
                any()
            )
        } throws ServiceJourneyNotFoundException("no match")
        every {
            findServiceJourney.matchServiceJourney(
                any(),
                any(),
                "DY456",
                any()
            )
        } returns ServiceJourney(
            serviceJourneyId = "AVI:ServiceJourney:DY456_hash",
            lineRefElement = LineRefWrapper(ref = "AVI:LineRef:SK_OSL-BGO")
        )

        val result = resolver.resolve(listOf(createFlight("SK123"), createFlight("DY456")))

        Assertions.assertNull(result[0].serviceJourneyRef)
        Assertions.assertEquals("AVI:ServiceJourney:DY456_hash", result[1].serviceJourneyRef)
    }

    @Test
    fun `should return full list regardless of match failures`() {
        every {
            findServiceJourney.matchServiceJourney(
                any(),
                any(),
                any(),
                any()
            )
        } throws ServiceJourneyNotFoundException("no match")

        val result = resolver.resolve(listOf(createFlight("SK123"), createFlight("DY456"), createFlight("WF789")))

        Assertions.assertEquals(3, result.size)
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