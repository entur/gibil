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
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import routes.api.AvinorApiHandler
import routes.api.AvinorXmlFeedParams

/**
 * Service that fetches flight data from all Avinor airports and merges
 * departure/arrival data for the same flight (matched by uniqueID).
 * Uses coroutines with batching for concurrent API calls.
 */
@Service
class FlightAggregationService(
    private val avinorApiHandler: AvinorApiHandler,
    private val xmlHandler: AvinorScheduleXmlHandler
) {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Fetches flight data from all airports concurrently and merges flights by uniqueID.
     * @return Map of uniqueID to merged Flight with complete departure and arrival data
     */
    fun fetchAndMergeAllFlights(): Map<String, Flight> = runBlocking {
        val airportCodes = loadAirportCodes()
        val flightMap = mutableMapOf<String, Flight>()

        println("Starting data fetch for ${airportCodes.size} airports...")

        airportCodes.chunked(BATCH_SIZE).forEach { batch ->
            processBatch(batch, flightMap)
        }

        println("Aggregated ${flightMap.size} unique flights from ${airportCodes.size} airports")
        flightMap
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

    //Fetches flights for a single airport.
    private fun fetchFlightsForAirport(airportCode: String): List<Flight> {
        return try {
            val url = avinorApiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParams(airportCode = airportCode)
            )
            val xmlResponse = avinorApiHandler.apiCall(url) ?: return emptyList()

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
