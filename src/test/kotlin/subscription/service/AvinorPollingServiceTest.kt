package org.gibil.subscription

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import model.FlightStop
import model.UnifiedFlight
import org.gibil.subscription.repository.FlightStateCache
import org.gibil.subscription.service.AvinorPollingService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import service.FlightAggregationService
import siri.SiriETMapper
import subscription.SubscriptionManager
import uk.org.siri.siri21.Siri
import java.time.LocalDate
import java.time.LocalDateTime

private fun mockFlight(
    flightId: String,
    date: String,
    stops: List<FlightStop> = listOf(defaultStop())
): UnifiedFlight {
    return UnifiedFlight(
        flightId = flightId,
        operator = "WF",
        date = LocalDate.parse(date),
        stops = stops
    )
}

private fun defaultStop(
    airportCode: String = "OSL",
    departureStatusCode: String? = "N",
    arrivalStatusCode: String? = "N"
): FlightStop {
    return FlightStop(
        airportCode = airportCode,
        arrivalTime = LocalDateTime.of(2026, 2, 26, 10, 0),
        departureTime = LocalDateTime.of(2026, 2, 26, 11, 0),
        departureStatusCode = departureStatusCode,
        departureStatusTime = null,
        arrivalStatusCode = arrivalStatusCode,
        arrivalStatusTime = null
    )
}

@ExtendWith(MockKExtension::class)
class AvinorPollingServiceTest {

    @MockK lateinit var flightAggregationService: FlightAggregationService
    @MockK lateinit var flightStateCache: FlightStateCache
    @MockK lateinit var siriETMapper: SiriETMapper
    @MockK lateinit var subscriptionManager: SubscriptionManager

    private lateinit var service: AvinorPollingService

    @BeforeEach
    fun setup() {
        service = AvinorPollingService(
            flightAggregationService,
            flightStateCache,
            siriETMapper,
            subscriptionManager
        )
        // Shared stubs used in most tests
        every { flightStateCache.getCacheSize() } returns 1
        every { flightStateCache.cacheKey(any()) } answers {
            val flight = firstArg<UnifiedFlight>()
            "${flight.flightId}_${flight.date}"
        }
        every { flightStateCache.cleanCache(any()) } just Runs
    }

    @Test
    fun `should map and push when changes are detected`() {
        val flights = listOf(mockFlight("WF844", "2026-02-26"))
        val siriPayload = mockk<Siri>()

        every { flightAggregationService.fetchUnifiedFlights() } returns flights
        every { flightStateCache.filterChanged(flights) } returns flights
        every { siriETMapper.mapUnifiedFlightsToSiri(flights) } returns siriPayload
        every { subscriptionManager.pushSiriToSubscribers(siriPayload) } just Runs

        service.pollAndPushUpdates()

        verify { siriETMapper.mapUnifiedFlightsToSiri(flights) }
        verify { subscriptionManager.pushSiriToSubscribers(siriPayload) }
    }

    @Test
    fun `should not push when no changes are detected`() {
        val flights = listOf(mockFlight("WF844", "2026-02-26"))

        every { flightAggregationService.fetchUnifiedFlights() } returns flights
        every { flightStateCache.filterChanged(flights) } returns emptyList()

        service.pollAndPushUpdates()

        verify(exactly = 0) { siriETMapper.mapUnifiedFlightsToSiri(any()) }
        verify(exactly = 0) { subscriptionManager.pushSiriToSubscribers(any()) }
    }

    @Test
    fun `should clean cache before filtering`() {
        val flights = listOf(mockFlight("WF844", "2026-02-26"))

        every { flightAggregationService.fetchUnifiedFlights() } returns flights
        every { flightStateCache.filterChanged(flights) } returns emptyList()

        service.pollAndPushUpdates()

        verifyOrder {
            flightStateCache.cleanCache(any())
            flightStateCache.filterChanged(any())
        }
    }

    @Test
    fun `should clean cache with correct keys`() {
        val flight = mockFlight("WF844", "2026-02-26")
        val flights = listOf(flight)

        every { flightAggregationService.fetchUnifiedFlights() } returns flights
        every { flightStateCache.filterChanged(flights) } returns emptyList()

        service.pollAndPushUpdates()

        verify { flightStateCache.cleanCache(setOf("WF844_2026-02-26")) }
    }

    @Test
    fun `should not throw when fetchUnifiedFlights throws`() {
        every { flightAggregationService.fetchUnifiedFlights() } throws RuntimeException("API down")

        assertDoesNotThrow { service.pollAndPushUpdates() }
    }

    @Test
    fun `should not push when fetchUnifiedFlights throws`() {
        every { flightAggregationService.fetchUnifiedFlights() } throws RuntimeException("API down")

        service.pollAndPushUpdates()

        verify(exactly = 0) { subscriptionManager.pushSiriToSubscribers(any()) }
    }

    @Test
    fun `should handle empty flight list without pushing`() {
        every { flightAggregationService.fetchUnifiedFlights() } returns emptyList()
        every { flightStateCache.filterChanged(emptyList()) } returns emptyList()

        service.pollAndPushUpdates()

        verify { flightStateCache.cleanCache(emptySet()) }
        verify(exactly = 0) { subscriptionManager.pushSiriToSubscribers(any()) }
    }

    @Test
    fun `should only push changed flights, not all flights`() {
        val unchanged = mockFlight("WF100", "2026-02-26")
        val changed = mockFlight("WF844", "2026-02-26")
        val allFlights = listOf(unchanged, changed)
        val siriPayload = mockk<Siri>()

        every { flightAggregationService.fetchUnifiedFlights() } returns allFlights
        every { flightStateCache.filterChanged(allFlights) } returns listOf(changed)
        every { siriETMapper.mapUnifiedFlightsToSiri(listOf(changed)) } returns siriPayload
        every { subscriptionManager.pushSiriToSubscribers(siriPayload) } just Runs

        service.pollAndPushUpdates()

        verify { siriETMapper.mapUnifiedFlightsToSiri(listOf(changed)) }
        verify(exactly = 0) { siriETMapper.mapUnifiedFlightsToSiri(allFlights) }
    }
}