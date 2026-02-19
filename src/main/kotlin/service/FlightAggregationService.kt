package service

import handler.AvinorScheduleXmlHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.xmlFeedApi.Flight
import model.xmlFeedApi.FlightLeg
import model.xmlFeedApi.MultiLegFlight
import org.gibil.BATCH_SIZE
import org.gibil.REQUEST_DELAY_MS
import org.gibil.service.ApiService
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import routes.api.AvinorApiHandler
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import model.AvinorXmlFeedParams

/**
 * Service that fetches flight data from all Avinor airports and merges
 * departure/arrival data for the same flight (matched by uniqueID).
 * Uses coroutines with batching for concurrent API calls.
 */
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

    //Parses a timestamp string to ZonedDateTime.
    private fun parseTimestamp(timestamp: String?): ZonedDateTime? {
        if (timestamp.isNullOrBlank()) return null
        return try {
            ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME)
        } catch (_: DateTimeParseException) {
            try {
                java.time.LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(java.time.ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    /**
     * Merges raw flights into a map by uniqueID and applies the time window filter.
     * Reuses an already-fetched raw flight list to avoid a second API sweep.
     */
    private fun mergeRawFlights(rawFlights: List<Flight>): Map<String, Flight> {
        val flightMap = mutableMapOf<String, Flight>()
        rawFlights.forEach { flight ->
            val existing = flightMap[flight.uniqueID]
            flightMap[flight.uniqueID] = if (existing == null) flight else existing.mergeWith(flight)
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

            if ("Error" in xmlResponse) {
                return emptyList()
            }

            val airport = xmlHandler.unmarshallXmlToAirport(xmlResponse)
            airport.flightsContainer?.flight ?: emptyList()
        } catch (_: Exception) {
            emptyList()
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
        } catch (_: Exception) {
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

        airportCodes.chunked(BATCH_SIZE).forEach { batch ->
            val deferredResults = batch.map { airportCode ->
                async(ioDispatcher) {
                    delay(REQUEST_DELAY_MS.toLong())
                    val flights = fetchFlightsForAirport(airportCode)
                    flights.forEach { it.populateMergedFields(airportCode) }
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
     *
     * @param rawFlights List of raw Flight objects (not merged by uniqueID)
     * @return List of detected multi-leg flights
     */
    fun detectMultiLegFlights(rawFlights: List<Flight>): List<MultiLegFlight> {
        val domesticFlights = rawFlights.filter { it.domInt == "D" }
        val withFlightId = domesticFlights.filter { it.flightId != null }
        val flightIdGroups = withFlightId.groupBy { it.flightId!! }

        return flightIdGroups.mapNotNull { (flightId, flights) ->
            // Need at least 4 records for a 2-leg flight (2 dep + 2 arr)
            if (flights.size < 4) return@mapNotNull null
            buildMultiLegFlight(flightId, flights)
        }
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
            return null
        }

        // Build legs by matching departures with their corresponding arrivals
        val legs = mutableListOf<FlightLeg>()
        val usedArrivals = mutableSetOf<Int>()

        for ((depIndex, departure) in departures.withIndex()) {
            val depAirport = departure.departureAirport
            val arrAirport = departure.arrivalAirport

            if (depAirport == null || arrAirport == null) {
                return null
            }

            // Find matching arrival: same route (depAirport → arrAirport)
            val matchingArrivalIndex = arrivals.indices.firstOrNull { arrIndex ->
                !usedArrivals.contains(arrIndex) &&
                arrivals[arrIndex].departureAirport == depAirport &&
                arrivals[arrIndex].arrivalAirport == arrAirport
            }

            if (matchingArrivalIndex == null) {
                return null
            }

            val arrival = arrivals[matchingArrivalIndex]
            usedArrivals.add(matchingArrivalIndex)

            legs.add(FlightLeg(
                legNumber = depIndex + 1,
                departureAirport = depAirport,
                arrivalAirport = arrAirport,
                scheduledDepartureTime = departure.scheduledDepartureTime,
                scheduledArrivalTime = arrival.scheduledArrivalTime,
                expectedDepartureTime = departure.departureStatus?.time,
                expectedArrivalTime = arrival.arrivalStatus?.time,
                departureStatus = departure.departureStatus,
                arrivalStatus = arrival.arrivalStatus,
                uniqueId = departure.uniqueID
            ))
        }

        if (legs.isEmpty()) {
            return null
        }

        // Sort legs by departure time to ensure correct order
        val sortedLegs = legs.sortedBy { it.scheduledDepartureTime }

        // Validate leg continuity: arrival airport of leg N must equal departure airport of leg N+1
        for (i in 0 until sortedLegs.size - 1) {
            if (sortedLegs[i].arrivalAirport != sortedLegs[i + 1].departureAirport) {
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
     * first departure and last arrival must be within the time window.
     * @return Pair of (direct flights map, multi-leg flights list)
     */
    fun fetchAllFlights(): Pair<Map<String, Flight>, List<MultiLegFlight>> {
        val rawFlights = fetchAllRawFlights()
        val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)
        val minTime = now.minusMinutes(MAX_PAST_MINUTES)
        val maxTime = now.plusHours(MAX_FUTURE_HOURS)

        val multiLegFlights = detectMultiLegFlights(rawFlights).filter { mlf ->
            val firstDep = parseTimestamp(mlf.legs.first().scheduledDepartureTime)
            val lastArr  = parseTimestamp(mlf.legs.last().scheduledArrivalTime)
            val depWithinTime = firstDep == null || (firstDep.isAfter(minTime) && firstDep.isBefore(maxTime))
            val arrWithinTime = lastArr  == null || (lastArr.isAfter(minTime)  && lastArr.isBefore(maxTime))
            depWithinTime && arrWithinTime
        }

        val directFlights = mergeRawFlights(rawFlights)
        return Pair(directFlights, multiLegFlights)
    }
}
