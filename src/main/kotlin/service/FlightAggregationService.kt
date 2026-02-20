package service

import handler.AvinorScheduleXmlHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import model.xmlFeedApi.Flight
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

        airportCodes.chunked(PollingConfig.BATCH_SIZE).forEach { batch ->
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
                delay(PollingConfig.REQUEST_DELAY_MS.toLong())
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
     * Populates the merged fields based on whether this flight is a departure or arrival.
     * Call this after parsing from XML before merging with other flights.
     * @param queryAirportCode The airport code used in the API query
     */
    private fun populateMergedFields(flight: Flight, queryAirportCode: String) {
        if (flight.isDeparture()) {
            flight.departureAirport = queryAirportCode
            flight.arrivalAirport = flight.airport
            flight.scheduledDepartureTime = flight.scheduleTime
            flight.departureStatus = flight.status
        } else {
            flight.arrivalAirport = queryAirportCode
            flight.departureAirport = flight.airport
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
}
