package service

import handler.AvinorScheduleXmlHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.xmlFeedApi.Flight
import model.xmlFeedApi.FlightLeg
import model.xmlFeedApi.MultiLegFlight
import org.gibil.PollingConfig
import org.gibil.Dates
import util.DateUtil.parseTimestamp
import org.gibil.service.ApiService
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import routes.api.AvinorApiHandler
import java.time.ZonedDateTime
import model.AvinorXmlFeedParams
import org.slf4j.LoggerFactory

/**
 * Service that fetches flight data from all Avinor airports and merges
 * departure/arrival data for the same flight (matched by uniqueID).
 * Uses coroutines with batching for concurrent API calls.
 */

private val LOG = LoggerFactory.getLogger(FlightAggregationService::class.java)

@Service
class FlightAggregationService(
    private val avinorApiHandler: AvinorApiHandler,
    private val xmlHandler: AvinorScheduleXmlHandler,
    private val apiService: ApiService,
    private val ioDispatcher: CoroutineDispatcher
) {


    companion object {
        // Filter to only include flights within this time window
        const val MAX_PAST_MINUTES = 20L      // At most 20 minutes in the past
        const val MAX_FUTURE_HOURS = 7L       // Up to 7 hours in the future
    }

    /**
     * Fetches flight data from all airports concurrently and merges flights by uniqueID.
     * Filters out flights that are outside the configured time window
     * (more than [MAX_PAST_MINUTES] minutes in the past or more than [MAX_FUTURE_HOURS] hours in the future).
     * @return Map of uniqueID to merged Flight entries within the configured time window
     */
    fun fetchAndMergeAllFlights(): Map<String, Flight> = mergeRawFlights(fetchAllRawFlights())

    /**
     * Checks if the entire flight journey is within the allowed time window:
     * - If departure exists and is outside window, exclude the flight
     * - If arrival exists and is outside window, exclude the flight
     * - Flights with missing times are kept (for debugging other issues)
     */
    private fun isWithinTimeWindow(flight: Flight, now: ZonedDateTime): Boolean {
        val minTime = now.minusMinutes(MAX_PAST_MINUTES)
        val maxTime = now.plusHours(MAX_FUTURE_HOURS)

        val departureTime = parseTimestamp(flight.scheduledDepartureTime)
        val arrivalTime = parseTimestamp(flight.scheduledArrivalTime)

        // If departure exists and is outside window, exclude
        if (departureTime != null && (departureTime.isBefore(minTime) || departureTime.isAfter(maxTime))) {
            return false
        }

        // If arrival exists and is outside window, exclude
        if (arrivalTime != null && (arrivalTime.isBefore(minTime) || arrivalTime.isAfter(maxTime))) {
            return false
        }

        // Keep flights with missing times (for debugging) or with both times in window
        return true
    }

    /**
     * Merges raw flights into a map by uniqueID and applies the time window filter.
     * Reuses an already-fetched raw flight list to avoid a second API sweep.
     */
    private fun mergeRawFlights(rawFlights: List<Flight>): Map<String, Flight> {
        val flightMap = mutableMapOf<String, Flight>()
        rawFlights.forEach { flight ->
            val existing = flightMap[flight.uniqueID]
            flightMap[flight.uniqueID] = if (existing == null) flight else mergeFlights(existing, flight)
        }
        val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)
        return flightMap.filter { (_, flight) -> isWithinTimeWindow(flight, now) }
    }

    //Fetches flights for a single airport.
    private fun fetchFlightsForAirport(airportCode: String): List<Flight> {
        return try {
            val url = avinorApiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParams(airportCode = airportCode)
            )
            val xmlResponse = apiService.apiCall(url) ?: return emptyList()

            //TODO SHOULD EITHER BE REMOVED OR IMPROVED. CAN CAUSE PROBLEMS
            if ("Error" in xmlResponse) {
                LOG.error("API returned error for {}: {}", airportCode, xmlResponse)
                return emptyList()
            }

            val airport = xmlHandler.unmarshallXmlToAirport(xmlResponse)
            airport.flightsContainer?.flight ?: emptyList()
        } catch (e: Exception) {
            LOG.error("Error fetching data for {}: {}", airportCode, e.message)
            emptyList()
        }
    }

    /**
     * Populates the merged fields based on whether this flight is a departure or arrival.
     * Call this after parsing from XML before merging with other flights.
     * @param queryAirportCode The airport code used in the API query
     */
    private fun populateMergedFields(flight: Flight, queryAirportCode: String) {
        val viaList = flight.viaAirports
        if (flight.isDeparture()) {
            flight.departureAirport = queryAirportCode
            flight.arrivalAirport = viaList.firstOrNull() ?: flight.airport
            flight.scheduledDepartureTime = flight.scheduleTime
            flight.departureStatus = flight.status
        } else {
            flight.arrivalAirport = queryAirportCode
            flight.departureAirport = viaList.lastOrNull() ?: flight.airport
            flight.scheduledArrivalTime = flight.scheduleTime
            flight.arrivalStatus = flight.status
        }
    }

    /**
     * Merges data from another Flight with the same uniqueID.
     * Combines departure data from one airport with arrival data from another.
     * @param other The other Flight to merge with (must have same uniqueID)
     * @return A new Flight with combined data from both
     */
    private fun mergeFlights(existing: Flight, other: Flight): Flight {
        if (existing.uniqueID != other.uniqueID) {
            throw IllegalArgumentException("Cannot merge flights with different uniqueIDs: ${existing.uniqueID} vs ${other.uniqueID}")
        }

        return Flight().apply {

            // Basic fields - prefer non-null values
            uniqueID = existing.uniqueID
            airline = existing.airline ?: other.airline
            flightId = existing.flightId ?: other.flightId
            domInt = existing.domInt ?: other.domInt
            viaAirport = existing.viaAirport ?: other.viaAirport
            delayed = existing.delayed ?: other.delayed
            airlineDesignators = existing.airlineDesignators ?: other.airlineDesignators
            airlineNames = existing.airlineNames ?: other.airlineNames
            flightNumbers = existing.flightNumbers ?: other.flightNumbers
            operationalSuffixs = existing.operationalSuffixs ?: other.operationalSuffixs

            // Merge departure data
            departureAirport = existing.departureAirport ?: other.departureAirport
            scheduledDepartureTime = existing.scheduledDepartureTime ?: other.scheduledDepartureTime
            departureStatus = existing.departureStatus ?: other.departureStatus

            // Merge arrival data
            arrivalAirport = existing.arrivalAirport ?: other.arrivalAirport
            scheduledArrivalTime = existing.scheduledArrivalTime ?: other.scheduledArrivalTime
            arrivalStatus = existing.arrivalStatus ?: other.arrivalStatus

            isMerged = true
        }
    }

    /**
     * Merges a list of flights into the aggregated flight map.
     * Only includes domestic flights due to lacking data from international airports.
     */
    private fun mergeFlightsIntoMap(
        flights: List<Flight>,
        queryAirportCode: String,
        flightMap: MutableMap<String, Flight>
    ) {
        flights
            .filter { it.isDomestic() }
            .forEach { flight ->
                populateMergedFields(flight, queryAirportCode)

                val existingFlight = flightMap[flight.uniqueID]
                if (existingFlight == null) {
                    flightMap[flight.uniqueID] = flight
                } else {
                    flightMap[flight.uniqueID] = mergeFlights(existingFlight, flight)
                }
            }
    }

    //Loads airport codes from the airports.txt resource file.
    private fun loadAirportCodes(): List<String> {
        return try {
            ClassPathResource("airports.txt")
                .inputStream
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            LOG.error("Error loading airport codes: {}", e.message)
            emptyList()
        }
    }

    //Gets all merged flights as a list.
    fun fetchAllMergedFlightsAsList(): List<Flight> {
        return fetchAndMergeAllFlights().values.toList()
    }


    /**
     * Fetches raw (unmerged) flight data from all airports.
     * collapses flight segments that should be kept separate.
     * @return List of all raw Flight objects with populated merged fields
     */
    private fun fetchAllRawFlights(): List<Flight> = runBlocking {
        val airportCodes = loadAirportCodes()
        val allFlights = mutableListOf<Flight>()

        airportCodes.chunked(PollingConfig.BATCH_SIZE).forEach { batch ->
            val deferredResults = batch.map { airportCode ->
                async(ioDispatcher) {
                    delay(PollingConfig.REQUEST_DELAY_MS.toLong())
                    val flights = fetchFlightsForAirport(airportCode)
                    flights.forEach { populateMergedFields(it, airportCode) }
                    flights
                }
            }

            val results = deferredResults.awaitAll()
            results.forEach { flights ->
                val domesticFlights = flights.filter { it.domInt == "D" }
                allFlights.addAll(domesticFlights)
            }
        }
        allFlights
    }

    /**
     * Detects and builds multi-leg flights from raw flight data.
     *
     * Groups all raw flights by flight_id
     * For each flight_id groups with 4+ records, attempts to build MultiLegFlight
     * Validates leg continuity (arrival of leg N = departure of leg N+1)
     */

    /**
     * @param rawFlights List of raw Flight objects (not merged by uniqueID)
     * @return List of detected multi-leg flights
     */
    fun detectMultiLegFlights(rawFlights: List<Flight>): List<MultiLegFlight> {
        val domesticFlights = rawFlights.filter { it.domInt == "D" }
        val withFlightId = domesticFlights.filter { it.flightId != null }
        val allFlightIdGroups = withFlightId.groupBy { it.flightId!! }

        // A flight ID is a multi-leg candidate if EITHER:
        // 1. Any of its records has via_airport set (explicit indicator from Avinor), OR
        // 2. Its records reference 3+ distinct airports (a direct flight only ever touches 2,
        //    so 3+ guarantees a stopover and prevents false positives from repeated flight numbers)
        val multiLegCandidateIds = allFlightIdGroups.filter { (_, flights) ->
            val hasVia = flights.any { it.viaAirport != null }
            val distinctAirports = flights.flatMap {
                listOfNotNull(it.departureAirport, it.arrivalAirport)
            }.distinct().size
            hasVia || distinctAirports >= 3
        }.keys

        LOG.info("Multi-leg candidates: {} flight IDs (from {} total groups)", multiLegCandidateIds.size, allFlightIdGroups.size)

        val results = multiLegCandidateIds.mapNotNull { flightId ->
            val flights = allFlightIdGroups[flightId] ?: return@mapNotNull null
            if (flights.size < 4) {
                LOG.info("Skipping {} - only {} records", flightId, flights.size)
                return@mapNotNull null
            }
            val result = buildMultiLegFlight(flightId, flights)
            if (result == null) LOG.info("buildMultiLegFlight failed for {}", flightId)
            else LOG.info("Built multi-leg: {} ({} legs: {})", flightId, result.totalLegs, result.allStops)
            result
        }

        LOG.info("detectMultiLegFlights: {} built successfully", results.size)
        return results
    }

    /**
     * Builds a MultiLegFlight from a group of flight records with the same flight_id.
     * Separates flights into departures and arrivals
     * Sorts each by schedule time
     * Matches departures with arrivals by airport to create legs
     * Validates that legs form a continuous chain
     *
     * @param flightId The flight number (e.g., "WF844")
     * @param flights All flight records with this flight_id
     * @return MultiLegFlight if valid, null otherwise
     */
    private fun buildMultiLegFlight(flightId: String, flights: List<Flight>): MultiLegFlight? {
        // Separate and sort by time
        val departures = flights
            .filter { it.isDeparture() }
            .sortedBy { it.scheduleTime }

        val arrivals = flights
            .filter { it.isArrival() }
            .sortedBy { it.scheduleTime }

        // Must have matching number of departures and arrivals
        if (departures.isEmpty() || departures.size != arrivals.size) {
            LOG.info("  {} dep/arr mismatch: {} deps, {} arrs", flightId, departures.size, arrivals.size)
            return null
        }

        // Build legs by pairing departures[i] with arrivals[i] (both sorted by scheduleTime).
        // Positional matching is necessary because the Avinor API sometimes reports flight.airport
        // as the full journey origin rather than the immediately adjacent stop, making airport-based
        // matching unreliable. Time-order pairing is always correct: the i-th departure in time
        // corresponds to the i-th arrival in time for the same leg.
        val legs = departures.mapIndexed { i, departure ->
            val depAirport = departure.departureAirport
            val arrAirport = departure.arrivalAirport
            if (depAirport == null || arrAirport == null) {
                LOG.info("  {} dep[{}] missing airports: dep={} arr={}", flightId, i, depAirport, arrAirport)
                return null
            }
            val arrival = arrivals[i]
            FlightLeg(
                legNumber = i + 1,
                departureAirport = depAirport,
                arrivalAirport = arrAirport,
                scheduledDepartureTime = departure.scheduledDepartureTime,
                scheduledArrivalTime = arrival.scheduledArrivalTime,
                expectedDepartureTime = departure.departureStatus?.time,
                expectedArrivalTime = arrival.arrivalStatus?.time,
                departureStatus = departure.departureStatus,
                arrivalStatus = arrival.arrivalStatus,
                uniqueId = departure.uniqueID
            )
        }

        if (legs.isEmpty()) {
            return null
        }

        // Sort legs by departure time to ensure correct order
        val sortedLegs = legs.sortedBy { it.scheduledDepartureTime }

        // Validate leg continuity: arrival airport of leg N must equal departure airport of leg N+1
        for (i in 0 until sortedLegs.size - 1) {
            if (sortedLegs[i].arrivalAirport != sortedLegs[i + 1].departureAirport) {
                LOG.info("  {} broken chain at leg {}: {}→{} but next departs {}",
                    flightId, i + 1,
                    sortedLegs[i].departureAirport, sortedLegs[i].arrivalAirport,
                    sortedLegs[i + 1].departureAirport)
                return null
            }
        }

        // Renumber legs after sorting
        val finalLegs = sortedLegs.mapIndexed { index, leg ->
            leg.copy(legNumber = index + 1)
        }

        // Extract via airports (all intermediate stops)
        val viaAirports = finalLegs.dropLast(1).map { it.arrivalAirport }


        return MultiLegFlight(
            flightId = flightId,
            airline = flights.first().airline ?: return null,
            legs = finalLegs,
            originAirport = finalLegs.first().departureAirport,
            destinationAirport = finalLegs.last().arrivalAirport,
            scheduledDepartureTime = finalLegs.first().scheduledDepartureTime,
            viaAirports = viaAirports
        )
    }

    /**
     * Single entry point for fetching all flights.
     * Performs one API sweep and derives both direct and multi-leg flights from it.
     * Multi-leg flight IDs are excluded from the direct flights map to avoid duplicates.
     * First departure and last arrival of a multi-leg flight must be within the time window.
     * @return Pair of (direct flights map, multi-leg flights list)
     */
    fun fetchAllFlights(): Pair<Map<String, Flight>, List<MultiLegFlight>> {
        val rawFlights = fetchAllRawFlights()
        val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)
        val minTime = now.minusMinutes(MAX_PAST_MINUTES)
        val maxTime = now.plusHours(MAX_FUTURE_HOURS)

        // Identify ALL candidate flight IDs (viaAirport or 3+ distinct airports) before building,
        // so that failed builds are also excluded from direct flights
        val domesticWithId = rawFlights.filter { it.domInt == "D" && it.flightId != null }
        val allFlightIdGroups = domesticWithId.groupBy { it.flightId!! }
        val allCandidateIds = allFlightIdGroups.filter { (_, flights) ->
            val hasVia = flights.any { it.viaAirport != null }
            val distinctAirports = flights.flatMap {
                listOfNotNull(it.departureAirport, it.arrivalAirport)
            }.distinct().size
            hasVia || distinctAirports >= 3
        }.keys

        val multiLegFlights = detectMultiLegFlights(rawFlights).filter { mlf ->
            val firstDep = parseTimestamp(mlf.legs.first().scheduledDepartureTime)
            val lastArr  = parseTimestamp(mlf.legs.last().scheduledArrivalTime)
            val depWithinTime = firstDep == null || (firstDep.isAfter(minTime) && firstDep.isBefore(maxTime))
            val arrWithinTime = lastArr  == null || (lastArr.isAfter(minTime)  && lastArr.isBefore(maxTime))
            if (!depWithinTime || !arrWithinTime) {
                LOG.info("Filtered out multi-leg {} (dep={} arr={} window={} to {})",
                    mlf.flightId, firstDep, lastArr, minTime, maxTime)
            }
            depWithinTime && arrWithinTime
        }

        // Exclude ALL candidate IDs from direct flights, even those that failed to build
        val directRawFlights = rawFlights.filter { it.flightId !in allCandidateIds }
        val directFlights = mergeRawFlights(directRawFlights)
        LOG.info("fetchAllFlights: {} direct, {} multi-leg", directFlights.size, multiLegFlights.size)
        return Pair(directFlights, multiLegFlights)
    }
}
