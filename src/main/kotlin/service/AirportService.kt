package service

import handler.AvinorScheduleXmlHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.gibil.BATCH_SIZE
import org.gibil.REQUEST_DELAY_MS
import org.gibil.service.ApiService
import org.springframework.stereotype.Service
import routes.api.AvinorApiHandler
import routes.api.AvinorXmlFeedParams
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Service class to handle fetching and processing airport data from the Avinor API.
 */
@Service
class AirportService(
    private val avinorApi: AvinorApiHandler,
    private val apiService: ApiService
) {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

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
            async(ioDispatcher) {
                delay(REQUEST_DELAY_MS.toLong())
                println("Sending request for $code")
                code to apiService.apiCall(
                    avinorApi.avinorXmlFeedUrlBuilder(
                        AvinorXmlFeedParams(airportCode = code, timeFrom = 2, timeTo = 7)
                    )
                )
            }
        }
    }
}