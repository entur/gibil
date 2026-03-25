package service

import model.UnifiedFlight
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import util.DateUtil.nanosToMs

private val LOG = LoggerFactory.getLogger(ServiceJourneyResolver::class.java)

/**
 * Resolves NeTEx service journey references for [UnifiedFlight] chains
 * by matching against the ExTime timetable via [FindServiceJourneyService].
 *
 * Runs after stitching and before SIRI mapping, so that [siri.SiriETMapper]
 * can act as a pure transformer without performing service lookups.
 *
 * Flights that cannot be matched are returned with [UnifiedFlight.serviceJourneyRef] = null
 * and will be excluded from SIRI output by the mapper. A summary count is logged on every call.
 */
@Service
class ServiceJourneyResolver(
    private val findServiceJourneyService: FindServiceJourneyService
) {

    fun resolve(flights: List<UnifiedFlight>): List<UnifiedFlight> {
        var matched = 0
        val workingMap = findServiceJourneyService.buildWorkingMap()

        val flightTimingsNs = mutableListOf<Long>()
        val totalStart = System.nanoTime()

        //Make the mutable list refill with all extime servicejourney data
        val resetStart = System.nanoTime()
        val resetMs = nanosToMs((System.nanoTime() - resetStart))
        LOG.info("resetMutableServiceJourneyList took $resetMs ms")

        val result = flights.map { flight ->
            val departureTimeStr = flight.stops.first().departureTime?.toString()

            if (departureTimeStr == null) {
                LOG.warn("No departure time for resolution: flightId={}", flight.flightId)
                return@map flight
            }

            val flightStart = System.nanoTime()
            val resolved = try {
                val lineRefInfo = listOf(flight.origin, flight.destination)
                val match = findServiceJourneyService.matchServiceJourney(workingMap, departureTimeStr, flight.flightId, lineRefInfo)
                matched++
                flight.copy(serviceJourneyRef = match.serviceJourneyId, lineRef = match.lineRef)
            } catch (e: ServiceJourneyNotFoundException) {
                LOG.debug("No NeTEx match for {}: {}", flight.flightId, e.message)
                flight
            } catch (e: Exception) {
                LOG.error("Resolution error for {}: {}", flight.flightId, e.message)
                flight
            }
            flightTimingsNs += System.nanoTime() - flightStart

            resolved
        }

        val totalMs = nanosToMs((System.nanoTime() - totalStart))

        if (flightTimingsNs.isNotEmpty()) {
            val sortedMs = flightTimingsNs.sorted().map { nanosToMs(it) }
            val meanMs = sortedMs.average()
            val maxMs = sortedMs.last()

            LOG.info(
                "Service journey resolution complete: $matched/${flights.size} flights matched | " +
                        "total=${totalMs}ms reset=${resetMs}ms | per-flight mean=${meanMs}ms max=${maxMs}ms",
            )
        } else {
            LOG.info(
                "Service journey resolution complete: {}/{} flights matched | total={}ms",
                matched, flights.size, totalMs
            )
        }

        return result
    }
}