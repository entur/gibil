package subscription.repository

import org.gibil.subscription.repository.FlightStateCache
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import subscriptiontest.service.ServiceTestHelper

class FlightStateCacheTest {

    private lateinit var cache : FlightStateCache

    @BeforeEach
    fun setup(){
        cache = FlightStateCache()
    }

    @Nested
    inner class HasChanged {

        @Test
        fun `hasChanged should return true if flight is new`() {
            val flight = ServiceTestHelper.mockFlight("WF921", "2026-03-23")

            val result = cache.hasChanged(flight)

            Assertions.assertTrue(result)
        }

        @Test
        fun `hasChanged should return false if flight and state are unchanged`() {
            val flight = ServiceTestHelper.mockFlight("WF921", "2026-03-23")

            cache.hasChanged(flight)
            val result = cache.hasChanged(flight)

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
        fun `hasChanged should treat a flight as new after it has been cleaned from the cache`(){
            val flight = ServiceTestHelper.mockFlight("WF921", "2026-03-24")
            val flight1 = ServiceTestHelper.mockFlight("WF922", "2026-03-24")
            val flight2 = ServiceTestHelper.mockFlight("WF923", "2026-03-24")
            val currentKeys = listOf(flight1, flight2)

            cache.hasChanged(flight)
            cache.cleanCache(currentKeys.map {cache.cacheKey(it)}.toSet())
            val result = cache.hasChanged(flight)

            Assertions.assertTrue(result)
        }
    }

    @Nested
    inner class FilterChanged {
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
    }

    @Nested
    inner class PopulateCache  {
        @Test
        fun `populateCache should store all entries when called`() {
            val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
            val flight2 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")

            cache.populateCache(listOf(flight1, flight2))

            Assertions.assertEquals(2, cache.getCacheSize())
        }

        @Test
        fun `populateCache should create a baseline for change detection`() {
            val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
            val flight2 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")

            cache.populateCache(listOf(flight1, flight2))

            val result = cache.hasChanged(flight1)
            Assertions.assertFalse(result)
        }
    }

    @Nested
    inner class CleanCache {
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
        fun `cleanCache should not remove entries that are still in the new flight set`(){
            val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
            val flight2 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")

            cache.populateCache(listOf(flight1, flight2))

            val flight3 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
            val flight4 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")
            val currentKeys = listOf(flight3, flight4)

            cache.cleanCache(currentKeys.map { cache.cacheKey(it) }.toSet())

            Assertions.assertEquals(2, cache.getCacheSize())
        }

        @Test
        fun `cleanCache should handle empty current flight set by clearing all entries`(){
            val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
            val flight2 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")

            cache.populateCache(listOf(flight1, flight2))

            cache.cleanCache(emptySet())

            Assertions.assertEquals(0, cache.getCacheSize())
        }
    }

    @Nested
    inner class CacheKey {
        @Test
        fun `cacheKey should produce consistent keys for the same flight`() {
            val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
            val flight2 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")

            val key1 = cache.cacheKey(flight1)
            val key2 = cache.cacheKey(flight2)

            Assertions.assertEquals(key1, key2)
        }

        @Test
        fun `cacheKey should produce different keys for same flightIDs on different dates`() {
            val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
            val flight2 = ServiceTestHelper.mockFlight("WF921", "2026-03-24")

            val key1 = cache.cacheKey(flight1)
            val key2 = cache.cacheKey(flight2)

            Assertions.assertNotEquals(key1, key2)
        }

        @Test
        fun `cacheKey should produce different keys for different flightIDs on same date`(){
            val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-23")
            val flight2 = ServiceTestHelper.mockFlight("WF922", "2026-03-23")

            val key1 = cache.cacheKey(flight1)
            val key2 = cache.cacheKey(flight2)

            Assertions.assertNotEquals(key1, key2)
        }
    }

    @Nested
    inner class ComputeFlightHash {
        @Test
        fun `computeFlightHash should include all stops and any change in later stops should return changed=true`() {
            val flight1 = ServiceTestHelper.mockFlight("WF921", "2026-03-24", stops = listOf(ServiceTestHelper.defaultStop(departureStatusCode = "E"), ServiceTestHelper.defaultStop(arrivalStatusCode = "D")))
            cache.hasChanged(flight1)

            val flight2 = ServiceTestHelper.mockFlight("WF921", "2026-03-24", stops = listOf(ServiceTestHelper.defaultStop(departureStatusCode = "E"), ServiceTestHelper.defaultStop(arrivalStatusCode = "A")))
            val result = cache.hasChanged(flight2)

            Assertions.assertTrue(result)
        }
    }
}