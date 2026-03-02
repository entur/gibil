package service

import handler.AvinorScheduleXmlHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.FlightKey
import model.FlightStop
import model.UnifiedFlight
import model.xmlFeedApi.Flight
import org.gibil.Dates
import org.gibil.FlightCodes
import org.gibil.PollingConfig
import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedApiHandler
import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedParamsLogic
import org.gibil.service.ApiService
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import util.DateUtil.parseTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val LOG = LoggerFactory.getLogger(FlightAggregationService::class.java)


/**
 * Service that fetches flight data from all Avinor airports and stitches
 * records into [model.UnifiedFlight] chains for both the /siri endpoint and the subscription polling path.
 * Uses coroutines with batching for concurrent API calls.
 */
@Service
class FlightAggregationService(
    private val avinorXmlFeedApiHandler: AvinorXmlFeedApiHandler,
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
     * Fetches raw flight records from the Avinor XML feed for a single airport.
     *
     * @param airportCode The IATA code of the airport to fetch flights for.
     * @return The list of [Flight] records, or an empty list if the call fails or returns an error.
     */
    private fun fetchFlightsForAirport(airportCode: String): List<Flight> {
        return try {
            val url = avinorXmlFeedApiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParamsLogic(airportCode = airportCode)
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
     * Reads the list of Avinor airport IATA codes from the `airports.txt` classpath resource.
     *
     * @return The list of airport codes, or an empty list if the file cannot be read.
     */
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

    /**
     * Wraps a raw Avinor [Flight] with the airport it was fetched from and its parsed schedule time.
     * Used as an intermediate representation before stop stitching.
     */
    private data class TaggedFlight(
        val sourceAirport: String,
        val raw: Flight,
        val time: LocalDateTime
    )

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
        airportCodes.chunked(PollingConfig.BATCH_SIZE).forEach { batch ->
            coroutineScope {
                batch.map { code ->
                    async(ioDispatcher) {
                        delay(PollingConfig.REQUEST_DELAY_MS.toLong())
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

    /**
     * Returns true if the flight should be treated as domestic.
     * Svalbard ([FlightCodes.SVALBARD_AIRPORTS]) routes are classified as international by Avinor
     * but are included because they are operated as domestic flights.
     *
     * @param sourceAirport The airport the flight record was fetched from.
     * @param flight The raw Avinor flight record.
     */
    private fun isDomesticOrSvalbard(sourceAirport: String, flight: Flight): Boolean {
        if (flight.domInt == FlightCodes.DOMESTIC_CODE) return true
        return sourceAirport == FlightCodes.SVALBARD_AIRPORTS || flight.airport == FlightCodes.SVALBARD_AIRPORTS
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

        /**
         * Walk all events in chronological order and group consecutive events at the same airport
         * into a single stop. An intermediate airport (e.g. BOO in TOS→BOO→SVJ) produces one stop
         * with both an arrival and a departure. The origin has only a departure, the destination
         * only an arrival.
         *
         * The three "cursor" variables track whichever airport is currently being assembled.
         * currentAirport is null at the start because no airport has been visited yet.
         * When the airport changes, the completed stop is saved ("flushed") to the list.
         */
        var currentAirport: String? = null
        var currentArrival: TaggedFlight? = null
        var currentDeparture: TaggedFlight? = null

        for (event in sortedEvents) {
            if (event.sourceAirport != currentAirport) {
                // Airport has changed. The null check prevents a spurious flush before
                // the very first airport has been seen (currentAirport starts as null).
                if (currentAirport != null) {
                    stops.add(buildFlightStop(currentAirport, currentArrival, currentDeparture))
                }
                currentAirport = event.sourceAirport  // move cursor to the new airport
                currentArrival = null                 // reset — new airport, fresh slate
                currentDeparture = null
            }
            if (event.raw.arrDep == FlightCodes.ARRIVAL_CODE) currentArrival = event
            if (event.raw.arrDep == FlightCodes.DEPARTURE_CODE) currentDeparture = event
        }
        // The loop only flushes a stop when the airport changes, so the last airport in the
        // sequence is never flushed inside the loop. Flush it here.
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
                val originAirport = events.firstOrNull { it.raw.arrDep == FlightCodes.ARRIVAL_CODE }?.raw?.airport
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

        if (flightId.length < 2) return null
        val operator = flightId.take(2)
        return UnifiedFlight(flightId = flightId, operator = operator, date = date, stops = stops)
    }

    /**
     * Builds a [FlightStop] from arrival and/or departure events at a single airport.
     *
     * @param airportCode The IATA code of the airport.
     * @param arrivalEvent The arrival event at this airport, or null if none was observed.
     * @param departureEvent The departure event from this airport, or null if none was observed.
     * @return A [FlightStop] with all available time and status information populated.
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


    /**
     * Returns true if the flight chain falls within the allowed time window.
     * Uses the full set of stop times rather than only the first departure, so that
     * ongoing multi-leg flights (e.g. WF904 TOS→ALF→TOS) are retained even when their
     * first leg has already departed.
     *
     * A chain is kept if its latest stop time is within [MAX_PAST_MINUTES] of now,
     * and its earliest stop time is within [MAX_FUTURE_HOURS] of now.
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
