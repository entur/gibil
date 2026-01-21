package org.example

import config.App
import model.avinorApi.Airport
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis
import java.io.File
import org.springframework.stereotype.Service

const val BATCH_SIZE = 5
const val REQUEST_DELAY_MS = 50

/**
 * Service class to handle fetching and processing airport data from the Avinor API.
 */
@Service
class AirportService(private val components: App) {

    /**
     * Fetches and processes airport data for a list of airport codes read from a text file.
     * @param filePath Path to the text file containing airport codes, one per line.
     */
    fun fetchAndProcessAirports(filePath: String) = runBlocking {

        val file = File(filePath)
        if (!file.exists()) {
            println("Error: Could not find airport list at $filePath")
            return@runBlocking
        }

        val airportCodes = file.readLines().filter { it.isNotBlank() }
        println("Starting data fetch for ${airportCodes.size} airports...")

        val timeUsed = measureTimeMillis {
            airportCodes.chunked(BATCH_SIZE).forEach { batch ->
                processBatch(batch)
            }
        }

        println("\n==================================================")
        println("Total operation time: $timeUsed ms")
    }

    /**
     * Processes a batch of airport codes by fetching their data concurrently.
     * @param batch List of airport codes to process.
     */
    private suspend fun processBatch(batch: List<String>) = coroutineScope {
        val deferredResults = batch.map { code ->
            async(Dispatchers.IO) {
                delay(REQUEST_DELAY_MS.toLong())
                println("Sending request for $code")
                code to components.avinorApi.avinorXmlFeedApiCall(
                    airportCodeParam = code,
                    timeFromParam = 2,
                    timeToParam = 7
                )
            }
        }

        val results = deferredResults.awaitAll()

        results.forEach { (code, xmlData) ->
            if (xmlData != null && "Error" !in xmlData) {
                try {
                    val airportObject = components.avxh.unmarshallXmlToAirport(xmlData)
                    printFlightDetails(airportObject)
                } catch (e: Exception) {
                    println("Could not parse data for $code: ${e.message}")
                }
            } else {
                println("Failed to fetch data for $code")
            }
        }
    }

    /**
     * Prints flight details for a given airport.
     * @param airportData Airport object containing flight information.
     */
    private fun printFlightDetails(airportData: Airport) {
        try {
            println("\n--------------------------------------------------")
            println("AIRPORT: ${airportData.name}")
            println("%-10s %-10s %-15s %-10s %-20s".format(
                "FLIGHT",
                "DIR",
                "DEST",
                "TIME",
                "STATUS"
            ))
            println("\n--------------------------------------------------")

            airportData.flightsContainer?.flight?.forEach { flight ->
                val direction = if (flight.arrDep == "A") "From(A)" else "To  (D)"
                val scheduledTime = flight.scheduleTime?.substring(11, 16) ?: "??"
                val newTime = flight.status?.time?.substring(11, 16)

                val statusText = when(flight.status?.code) {
                    "A" -> "Arrived ($newTime)"
                    "D" -> "Departed ($newTime)"
                    "E" -> "New Time: $newTime"
                    "C" -> "Cancelled"
                    else -> ""
                }

                println("%-10s %-10s %-15s %-10s %-20s".format(
                    flight.flightId,
                    direction,
                    flight.airport,
                    scheduledTime,
                    statusText
                ))
            }
        } catch (e: Exception) {
            println("Error printing flight details: ${e.message}")
        }
    }
}