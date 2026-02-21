package service

import handler.AvinorScheduleXmlHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.AvinorXmlFeedParams
import model.FlightStop
import model.UnifiedFlight
import model.xmlFeedApi.Flight
import org.gibil.BATCH_SIZE
import org.gibil.Dates
import org.gibil.REQUEST_DELAY_MS
import org.gibil.SVALBARD_AIRPORTS
import org.gibil.service.ApiService
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import routes.api.AvinorApiHandler
import util.DateUtil.parseTimestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

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
    private val apiService: ApiService
) {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

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
    fun fetchAndMergeAllFlights(): Map<String, Flight> = runBlocking {
        val airportCodes = loadAirportCodes()
        val flightMap = mutableMapOf<String, Flight>()

        LOG.info("Starting data fetch for {} airports...", airportCodes.size)

        airportCodes.chunked(BATCH_SIZE).forEach { batch ->
            processBatch(batch, flightMap)
        }

        // Filter out flights where either end is outside the allowed time window
        val filteredFlights = flightMap.filter { (_, flight) ->
            isWithinTimeWindow(flight, Dates.instantNowUtc())
        }

        LOG.info("Aggregated {} flights within time window from {} total ({} airports)", filteredFlights.size, flightMap.size, airportCodes.size)
        filteredFlights
    }

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

    //Processes a batch of airport codes concurrently.
    private suspend fun processBatch(
        batch: List<String>,
        flightMap: MutableMap<String, Flight>
    ) = coroutineScope {
        val deferredResults = batch.map { airportCode ->
            async(ioDispatcher) {
                delay(REQUEST_DELAY_MS.toLong())
                airportCode to fetchFlightsForAirport(airportCode)
            }
        }

        val results = deferredResults.awaitAll()

        results.forEach { (airportCode, flights) ->
            mergeFlightsIntoMap(flights, airportCode, flightMap)
        }
    }

    //Fetches flights for a single airport using wider time window.
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
     * Merges a list of flights into the aggregated flight map.
     * Only includes domestic flights due to lacking data from international airports.
     */
    private fun mergeFlightsIntoMap(
        flights: List<Flight>,
        queryAirportCode: String,
        flightMap: MutableMap<String, Flight>
    ) {
        flights
            .filter { it.domInt == "D" }
            .forEach { flight ->
                flight.populateMergedFields(queryAirportCode)

                val existingFlight = flightMap[flight.uniqueID]
                if (existingFlight == null) {
                    flightMap[flight.uniqueID] = flight
                } else {
                    flightMap[flight.uniqueID] = existingFlight.mergeWith(flight)
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

    // Wraps a raw Avinor Flight with the airport it was fetched from and its parsed schedule time.
    private data class TaggedFlight(
        val sourceAirport: String,
        val raw: Flight,
        val time: LocalDateTime
    )

    private data class FlightKey(val flightId: String, val date: LocalDate)

    /**
     * Fetches flight data from all airports and stitches records into UnifiedFlight chains.
     * Handles both direct (2 stops) and multi-leg (3+ stops) flights, including Svalbard routes.
     * Used by the /siri endpoint.
     */
    fun fetchUnifiedFlights(): List<UnifiedFlight> = runBlocking {
        val airportCodes = loadAirportCodes()

        if (airportCodes.isEmpty()) {
            LOG.error("Airport list is empty")
            return@runBlocking emptyList()
        }

        val allTaggedFlights = mutableListOf<TaggedFlight>()

        LOG.info("Starting unified flight fetch for {} airports...", airportCodes.size)

        // FETCH: Collect all domestic (+ Svalbard) flights with their parsed schedule times
        airportCodes.chunked(BATCH_SIZE).forEach { batch ->
            coroutineScope {
                batch.map { code ->
                    async(ioDispatcher) {
                        delay(REQUEST_DELAY_MS.toLong())
                        code to fetchFlightsForAirport(code)
                    }
                }.awaitAll()
            }.forEach { (code, flights) ->
                flights
                    .filter { isDomesticOrSvalbard(code, it) }
                    .forEach { flight ->
                        parseTime(flight.scheduleTime)?.let { parsedTime ->
                            allTaggedFlights.add(TaggedFlight(code, flight, parsedTime))
                        }
                    }
            }
        }

        // GROUP: by flightId + date to avoid mixing flights from different days
        val grouped = allTaggedFlights.groupBy {
            FlightKey(it.raw.flightId ?: "UNKNOWN", it.time.toLocalDate())
        }

        // STITCH: convert each group into an ordered chain of stops
        val unifiedFlights = grouped.mapNotNull { (key, events) ->
            if (key.flightId == "UNKNOWN") null
            else stitchFlightLegs(key.flightId, key.date, events)
        }

        val filteredChains = unifiedFlights.filter { isChainWithinTimeWindow(it, Dates.instantNowUtc()) }
        LOG.info("Aggregated {} valid flight chains within time window from {} total.", filteredChains.size, unifiedFlights.size)
        filteredChains
    }

    // Svalbard (LYR) flights are classified as international ("I") by Avinor,
    // but should be treated as domestic for our purposes.
    private fun isDomesticOrSvalbard(sourceAirport: String, flight: Flight): Boolean {
        if (flight.domInt == "D") return true
        return sourceAirport in SVALBARD_AIRPORTS || flight.airport in SVALBARD_AIRPORTS
    }

    /**
     * Stitches individual flight events (arrivals/departures at different airports)
     * into a single ordered chain of stops. Handles direct (2 stops), multi-leg (3+),
     * and circular flights (origin == destination, e.g. BOO→RET→LKN→BOO).
     *
     * Returns null if the chain is invalid (gaps between stops, or fewer than 2 stops).
     */
    private fun stitchFlightLegs(flightId: String, date: LocalDate, events: List<TaggedFlight>): UnifiedFlight? {
        if (events.isEmpty()) return null

        val sortedEvents = events.sortedBy { it.time }
        val stops = mutableListOf<FlightStop>()

        // Build stops chronologically. Consecutive events at the same airport are merged
        // into one stop (arrival then departure at an intermediate airport).
        // Events at the same airport separated by other airports become separate stops —
        // essential for circular flights (e.g. BOO→SVJ→LKN→BOO).
        var currentAirport: String? = null
        var currentArrival: TaggedFlight? = null
        var currentDeparture: TaggedFlight? = null

        for (event in sortedEvents) {
            if (event.sourceAirport != currentAirport) {
                if (currentAirport != null) {
                    stops.add(buildFlightStop(currentAirport, currentArrival, currentDeparture))
                }
                currentAirport = event.sourceAirport
                currentArrival = null
                currentDeparture = null
            }
            if (event.raw.arrDep == "A") currentArrival = event
            if (event.raw.arrDep == "D") currentDeparture = event
        }
        // Flush the last stop
        if (currentAirport != null) {
            stops.add(buildFlightStop(currentAirport, currentArrival, currentDeparture))
        }

        // Infer missing endpoint when only one side of a direct flight was seen
        // (e.g. one airport's API call failed or returned no data)
        if (stops.size == 1) {
            val onlyStop = stops.first()
            if (onlyStop.departureTime != null && onlyStop.targetAirport != null) {
                stops.add(FlightStop(airportCode = onlyStop.targetAirport, arrivalTime = null, departureTime = null))
            } else if (onlyStop.arrivalTime != null) {
                val originAirport = events.firstOrNull { it.raw.arrDep == "A" }?.raw?.let { arr ->
                    arr.viaAirport?.split(",")?.lastOrNull()?.trim() ?: arr.airport
                }
                if (originAirport != null) {
                    stops.add(0, FlightStop(airportCode = originAirport, arrivalTime = null, departureTime = null))
                }
            }
        }

        if (stops.size < 2) return null

        // Gap detection: if we leave A going to B, but next stop is C, the chain is invalid
        for (i in 0 until stops.size - 1) {
            val current = stops[i]
            val next = stops[i + 1]
            if (current.departureTime != null &&
                current.targetAirport != null &&
                current.targetAirport != next.airportCode
            ) {
                return null
            }
        }

        val operator = flightId.take(2)
        return UnifiedFlight(flightId = flightId, operator = operator, date = date, stops = stops)
    }

    /**
     * Builds a FlightStop from arrival and/or departure events at a single airport.
     */
    private fun buildFlightStop(
        airportCode: String,
        arrivalEvent: TaggedFlight?,
        departureEvent: TaggedFlight?
    ): FlightStop {
        // Avinor's via_airport lists ALL remaining intermediate stops as comma-separated
        // (e.g. "RET,LKN" for BOO→RET→LKN→BOO). We only need the NEXT stop for gap detection.
        val target = departureEvent?.raw?.let { dep ->
            dep.viaAirport?.split(",")?.firstOrNull()?.trim() ?: dep.airport
        }
        return FlightStop(
            airportCode = airportCode,
            arrivalTime = arrivalEvent?.time,
            departureTime = departureEvent?.time,
            departureStatusCode = departureEvent?.raw?.status?.code,
            departureStatusTime = departureEvent?.raw?.status?.time?.let { parseTime(it) },
            arrivalStatusCode = arrivalEvent?.raw?.status?.code,
            arrivalStatusTime = arrivalEvent?.raw?.status?.time?.let { parseTime(it) },
            targetAirport = target
        )
    }

    // Parses a schedule time string into LocalDateTime, using the shared DateUtil parser.
    private fun parseTime(timeStr: String?): LocalDateTime? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            parseTimestamp(timeStr)?.toLocalDateTime()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Keeps a chain if its latest stop time is within the allowed window and its earliest
     * stop time is not beyond the future limit. Mirrors [isWithinTimeWindow] but operates
     * on the full set of stop times rather than a single departure/arrival pair, so that
     * ongoing multi-leg flights (e.g. WF904 TOS→ALF→TOS) are retained even when their
     * first departure is already in the past.
     */
    private fun isChainWithinTimeWindow(flight: UnifiedFlight, now: ZonedDateTime): Boolean {
        val minTime = now.minusMinutes(MAX_PAST_MINUTES)
        val maxTime = now.plusHours(MAX_FUTURE_HOURS)

        val allTimes = flight.stops.flatMap { stop ->
            listOfNotNull(
                stop.departureTime?.atZone(ZoneOffset.UTC),
                stop.arrivalTime?.atZone(ZoneOffset.UTC)
            )
        }

        if (allTimes.isEmpty()) return true

        // Drop chains whose last stop is already more than MAX_PAST_MINUTES in the past
        if (allTimes.max().isBefore(minTime)) return false
        // Drop chains that don't start within MAX_FUTURE_HOURS
        if (allTimes.min().isAfter(maxTime)) return false

        return true
    }
}
