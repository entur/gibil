package subscription

import model.xmlFeedApi.Flight
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the state of flights to determine if there have been changes since the last check.
 * It uses a ConcurrentHashMap to store the hash of the flight's relevant fields, keyed by the flight's unique ID.
 * The cache allows for efficient detection of changes in flight status, departure/arrival status, and gate information.
 */

@Component
class FlightStateCache {

    private val flightStateMap = ConcurrentHashMap<String, Int>()
    private val logger = LoggerFactory.getLogger(FlightStateCache::class.java)

    /**
     * Checks if each flight in the provided collection (all flights from the latest API response) has changed compared to the cached state.
     * It computes a hash for each flight based on its relevant fields and compares it to the
     * previously stored hash in the cache. If the hash has changed or if there is no previous hash (indicating a new flight),
     * it returns true, indicating that the flight has changed.
     * @param Flight The flight to check for changes.
     * @return true if the flight has changed or are new since the last check, false otherwise.
     */
    fun hasChanged(flight: Flight): Boolean {
        val currentHash = computeFlightHash(flight)
        val previousHash = flightStateMap.put(flight.uniqueID, currentHash)
        val changed = previousHash == null || previousHash != currentHash

        if (changed){
            logger.debug("Flight {} changed: previousHash={}, currentHash={}",
                flight.uniqueID, previousHash, currentHash)
        }
        return changed
    }

    /**
     * Filters the provided collection of flights to return only those that have changed compared to the cached state.
     * It uses the hasChanged method to determine which flights have changed and returns a list of those flights.
     * @param flights A collection of Flight objects to check for changes (allFlights in AvinorPollingService).
     * @return A list of Flight objects that have changed since the last check.
     */
    fun filterChanged(flights: Collection<Flight>): List<Flight> {
        return flights.filter { hasChanged(it) }
    }

    /**
     * Populate the cache with the initial state of flights. This should be called after the first successful fetch
     * of flight data to establish a baseline for change detection.
     * @param flights A collection of Flight objects to populate the cache with (here its the initial avinor API delivery).
     */
    fun populateCache(flights: Collection<Flight>) {
        logger.info("Populating cache with {} flights", flights.size)
        flights.forEach { flight ->
            flightStateMap[flight.uniqueID] = computeFlightHash(flight)
        }
        logger.info("Cache now contains {} entries", flightStateMap.size)
    }

    /**
     * A method to clean the cache by retaining only the entries for the current uniqueIDs.
     * This is useful to prevent the cache from growing indefinitely with old flight entries that are no longer relevant.
     * It takes all the uniqueIDs from the latest API call and chekcs if they are still in the map, if not it removes them.
     * It also logs the number of entries removed and the current size of the cache after cleaning.
     * @param currentFlightIds A set of uniqueIDs representing the current flights from the latest API response.
     */
    fun cleanCache(currentFlightIds: Set<String>){
        val beforeSize = flightStateMap.size
        flightStateMap.keys.retainAll(currentFlightIds)
        val removed = beforeSize - flightStateMap.size
        if (removed > 0) {
            logger.info("Cleaned cache: removed {} entries, current size {}", removed, flightStateMap.size)
        }

    }

    /**
     * Simple helper function to check current size of map for logging and testing purposes.
     * @return The number of entries currently in the cache. (Key-Value pairs of uniqueID and hash)
     */
    fun getCacheSize(): Int = flightStateMap.size

    /**
     * Computes a hash for a flight based on its relevant fields that are used to determine if the flight has changed.
     * The hash is computed using the status code and time for the flight's overall status,
     * departure status, and arrival status, as well as the gate information.
     * This allows for efficient comparison of flight states by comparing the computed hash values, rather than comparing each field individually.
     * @param flight The Flight object for which to compute the hash.
     * @return An integer hash representing the relevant state of the flight.
     */
    private fun computeFlightHash(flight: Flight): Int {
        return listOf(
            flight.status?.code,
            flight.status?.time,
            flight.departureStatus?.code,
            flight.departureStatus?.time,
            flight.arrivalStatus?.code,
            flight.arrivalStatus?.time,
            flight.gate,
        ).hashCode()
    }
}