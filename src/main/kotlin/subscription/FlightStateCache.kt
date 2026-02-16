package subscription

import model.avinorApi.Flight
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class FlightStateCache {

    private val flightStateMap = ConcurrentHashMap<String, Int>()
    private val logger = LoggerFactory.getLogger(FlightStateCache::class.java)

    fun hasChanged(flight: Flight): Boolean {
        val flightId = flight.flightId ?: return false
        val currentHash = computeFlightHash(flight)
        val previousHash = flightStateMap.put(flightId, currentHash)
        val changed = previousHash == null || previousHash != currentHash

        if (changed){
            logger.debug("Flight {} changed: previousHash={}, currentHash={}",
                flightId, previousHash, currentHash)
        }
        return changed
    }

    fun filterChanged(flights: Collection<Flight>): List<Flight> {
        return flights.filter { hasChanged(it) }
    }

    fun populateCache(flights: Collection<Flight>) {
        logger.info("Populating cache with {} flights", flights.size)
        flights.forEach { flight ->
            val flightId = flight.flightId ?: return@forEach
            flightStateMap[flightId] = computeFlightHash(flight)
        }
        logger.info("Cache now contains {} entries", flightStateMap.size)
    }

    fun getCacheSize(): Int = flightStateMap.size

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