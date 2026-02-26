package org.gibil.subscription.repository

import model.UnifiedFlight
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

private val LOG = LoggerFactory.getLogger(FlightStateCache::class.java)

/**
 * Caches the state of flights to determine if there have been changes since the last poll cycle.
 * Uses a ConcurrentHashMap keyed by "flightId_date" (e.g. "WF844_2026-02-26") to store a hash
 * of each [UnifiedFlight]'s stop statuses. A change in any stop's departure or arrival status
 * will produce a different hash, triggering a push to subscribers.
 */
@Component
class FlightStateCache {

    private val flightStateMap = ConcurrentHashMap<String, Int>()

    /**
     * Checks if a flight has changed compared to the cached state.
     * Computes a hash over all stop statuses and times and compares against the previously stored hash.
     * New flights (no previous hash) are treated as changed.
     * @param flight The [UnifiedFlight] to check.
     * @return true if the flight has changed or is new since the last check, false otherwise.
     */
    fun hasChanged(flight: UnifiedFlight): Boolean {
        val key = cacheKey(flight)
        val currentHash = computeFlightHash(flight)
        val previousHash = flightStateMap.put(key, currentHash)
        val changed = previousHash == null || previousHash != currentHash

        if (changed) {
            LOG.debug("Flight {} changed: previousHash={}, currentHash={}", key, previousHash, currentHash)
        }
        return changed
    }

    /**
     * Filters the provided collection to return only flights that have changed since the last poll cycle.
     * @param flights Collection of [UnifiedFlight] to check for changes.
     * @return List of [UnifiedFlight] that have changed since the last check.
     */
    fun filterChanged(flights: Collection<UnifiedFlight>): List<UnifiedFlight> {
        return flights.filter { hasChanged(it) }
    }

    /**
     * Populates the cache with the initial state of flights.
     * Should be called after the first successful fetch to establish a baseline for change detection.
     * @param flights Collection of [UnifiedFlight] representing the initial Avinor delivery.
     */
    fun populateCache(flights: Collection<UnifiedFlight>) {
        LOG.info("Populating cache with {} flights", flights.size)
        flights.forEach { flight ->
            flightStateMap[cacheKey(flight)] = computeFlightHash(flight)
        }
        LOG.info("Cache now contains {} entries", flightStateMap.size)
    }

    /**
     * Removes cache entries that are no longer present in the current flight set,
     * preventing the cache from growing indefinitely with stale entries.
     * @param currentFlightKeys Set of cache keys (see [cacheKey]) from the latest fetch.
     */
    fun cleanCache(currentFlightKeys: Set<String>) {
        val beforeSize = flightStateMap.size
        flightStateMap.keys.retainAll(currentFlightKeys)
        val removed = beforeSize - flightStateMap.size
        if (removed > 0) {
            LOG.info("Cleaned cache: removed {} entries, current size {}", removed, flightStateMap.size)
        }
    }

    /**
     * Returns the current number of entries in the cache, used for logging and testing.
     * @return Number of key-value pairs currently stored in the cache.
     */
    fun getCacheSize(): Int = flightStateMap.size

    /**
     * Produces a stable cache key for a [UnifiedFlight], e.g. "WF844_2026-02-26".
     * @param flight The flight to derive a key for.
     * @return String key combining flightId and date.
     */
    fun cacheKey(flight: UnifiedFlight): String = "${flight.flightId}_${flight.date}"

    /**
     * Computes a hash over all stop statuses and times in the flight chain.
     * A change in any stop's departure or arrival status code or time will produce a different hash.
     * @param flight The [UnifiedFlight] to hash.
     * @return Integer hash representing the current status state of the flight.
     */
    private fun computeFlightHash(flight: UnifiedFlight): Int {
        return flight.stops.flatMap { stop ->
            listOf(
                stop.departureStatusCode,
                stop.departureStatusTime,
                stop.arrivalStatusCode,
                stop.arrivalStatusTime
            )
        }.hashCode()
    }
}