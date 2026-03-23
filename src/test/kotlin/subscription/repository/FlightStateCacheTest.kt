package subscription.repository

import org.gibil.subscription.repository.FlightStateCache
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import subscriptiontest.service.ServiceTestHelper

class FlightStateCacheTest {

    private lateinit var cache : FlightStateCache

    @BeforeEach
    fun setup(){
        cache = FlightStateCache()
    }

    @Test
    fun `hasChanged should return true if flight is new`() {
        val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")

        val result = cache.hasChanged(flight1)

        Assertions.assertTrue(result)
    }

    @Test
    fun `hasChanged should return false if flight and state are unchanged`() {
        val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")

        cache.hasChanged(flight1)
        val result = cache.hasChanged(flight1)

        Assertions.assertFalse(result)
    }

    @Test
    fun `hasChanged should return true if flight state has changed`() {
        val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
        val flight2 = ServiceTestHelper.mockFlight("WF921", "2026-03-23", stops = listOf(ServiceTestHelper.defaultStop(departureStatusCode = "D")))

        cache.hasChanged(flight1)
        val result = cache.hasChanged(flight2)

        Assertions.assertTrue(result)
    }

    @Test
    fun `filterChanged should return only changed flights`() {
        val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
        val flight2 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")
        val flight3 = ServiceTestHelper.mockFlight("WF923", "2026-03-23")

        cache.hasChanged(flight1)
        cache.hasChanged(flight2)
        cache.hasChanged(flight3)

        val unchanged = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
        val changed = ServiceTestHelper.mockFlight("WF922", "2026-03-23", stops = listOf(ServiceTestHelper.defaultStop(departureStatusCode = "D")))
        val new = ServiceTestHelper.mockFlight("WF924", "2026-03-23")

        val flights = listOf(unchanged, changed, new)
        val result = cache.filterChanged(flights)

        Assertions.assertEquals(2, result.size)
        Assertions.assertTrue(result.contains(changed))
        Assertions.assertTrue(result.contains(new))
        Assertions.assertFalse(result.contains(unchanged))
    }

    @Test
    fun `populateCache should store all entries when called`() {
        val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
        val flight2 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")

        cache.populateCache(listOf(flight1, flight2))

        Assertions.assertEquals(2, cache.getCacheSize())
    }

    @Test
    fun `cleanCache should remove entries not in the new flight set`(){
        val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
        val flight2 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")
        val flight3 = ServiceTestHelper.mockFlight("WF923", "2026-03-23")

        cache.populateCache(listOf(flight1, flight2, flight3))

        val flight4 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")
        val flight5 = ServiceTestHelper.mockFlight("WF923", "2026-03-23")
        val currentKeys = listOf(flight4, flight5)

        cache.cleanCache(currentKeys.map { cache.cacheKey(it) }.toSet())

        Assertions.assertEquals(2, cache.getCacheSize())
    }

    @Test
    fun `cacheKey should produce consisten keys for the same flight`() {
        val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
        val flight2 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")

        val key1 = cache.cacheKey(flight1)
        val key2 = cache.cacheKey(flight2)
        val list = listOf(key1, key2)
        print(list)

        Assertions.assertEquals(key1, key2)
    }

    @Test
    fun `computeFlightHash should include all stops`() {

    }
}