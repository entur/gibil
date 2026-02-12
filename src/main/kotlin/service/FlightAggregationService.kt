package service

import handler.AvinorScheduleXmlHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.avinorApi.Flight
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
    fun fetchAndMergeAllFlights(): Map<String, Flight> = runBlocking {
        val airportCodes = loadAirportCodes()
        val flightMap = mutableMapOf<String, Flight>()

        println("Starting data fetch for ${airportCodes.size} airports...")

        airportCodes.chunked(BATCH_SIZE).forEach { batch ->
            processBatch(batch, flightMap)
        }

        // Filter out flights where either end is outside the allowed time window
        val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)
        val filteredFlights = flightMap.filter { (_, flight) ->
            isWithinTimeWindow(flight, now)
        }

        println("Aggregated ${filteredFlights.size} flights within time window from ${flightMap.size} total (${airportCodes.size} airports)")
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

    /**
     * Parses a timestamp string to ZonedDateTime.
     */
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

            if ("Error" in xmlResponse) {
                println("API returned error for $airportCode")
                return emptyList()
            }

            val airport = xmlHandler.unmarshallXmlToAirport(xmlResponse)
            airport.flightsContainer?.flight ?: emptyList()
        } catch (e: Exception) {
            println("Error fetching data for $airportCode: ${e.message}")
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
            println("Error loading airport codes: ${e.message}")
            emptyList()
        }
    }

    //Gets all merged flights as a list.
    fun fetchAllMergedFlightsAsList(): List<Flight> {
        return fetchAndMergeAllFlights().values.toList()
    }
}
