package routes.api

import model.AvinorXmlFeedParams
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import service.FlightAggregationService
import service.SiriEtService
import siri.SiriETMapper
import siri.SiriETPublisher
import org.gibil.service.ApiService

@RestController
class Endpoint(
    private val siriEtService: SiriEtService,
    private val avinorApiHandler: AvinorApiHandler,
    private val flightAggregationService: FlightAggregationService,
    private val siriETMapper: SiriETMapper,
    private val siriETPublisher: SiriETPublisher,
    private val apiService: ApiService
) {

    @GetMapping("/siri", produces = [MediaType.APPLICATION_XML_VALUE])
    fun siriEtEndpoint(@RequestParam(defaultValue = "OSL") airport: String): String {
        return siriEtService.fetchAndConvert(airport)
    }

    /**
     * SIRI-ET endpoint that aggregates data from ALL Avinor airports.
     * Merges departure and arrival data for complete EstimatedCalls.
     * Warning: Makes ~55 API calls, may take 30-60 seconds.
     
    @GetMapping("/siri/all", produces = [MediaType.APPLICATION_XML_VALUE])
    fun siriAllAirportsEndpoint(): String {
    val mergedFlights = flightAggregationService.fetchAllMergedFlightsAsList()
    val siri = siriETMapper.mapMergedFlightsToSiri(mergedFlights)
    return siriETPublisher.toXml(siri)
    }
     */


    /**
     * Debug endpoint that returns the raw XML response from the Avinor API.
     */
    @GetMapping("/avinor", produces = [MediaType.APPLICATION_XML_VALUE])
    fun rawAvinorEndpoint(
        @RequestParam(defaultValue = "OSL") airport: String
    ): String {
        val url = avinorApiHandler.avinorXmlFeedUrlBuilder(
            AvinorXmlFeedParams(airportCode = airport)
        )
        return apiService.apiCall(url) ?: "Error: No response from Avinor API"
    }

    /**
     * Debug endpoint that fetches and combines raw XML data from all Avinor airports.
     * Warning: This makes ~55 API calls and may take some time.
     
    @GetMapping("/avinor/all", produces = [MediaType.APPLICATION_XML_VALUE])
    fun allAirportsEndpoint(): String {
    val airportCodes = ClassPathResource("airports.txt")
    .inputStream
    .bufferedReader()
    .readLines()
    .filter { it.isNotBlank() }

    val combinedXml = StringBuilder()
    combinedXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    combinedXml.append("<AllAirportsResponse>\n")

    airportCodes.forEach { code ->
    try {
    val url = avinorApiHandler.avinorXmlFeedUrlBuilder(
    AvinorXmlFeedParams(airportCode = code)
    )
    val response = apiService.apiCall(url)
    if (response != null && "Error" !in response) {
    combinedXml.append("  <AirportData code=\"$code\">\n")
    val cleanedResponse = response
    .replace(Regex("<\\?xml[^>]*\\?>"), "")
    .trim()
    combinedXml.append("    $cleanedResponse\n")
    combinedXml.append("  </AirportData>\n")
    }
    } catch (e: Exception) {
    combinedXml.append("  <!-- Error fetching $code: ${e.message} -->\n")
    }
    }

    combinedXml.append("</AllAirportsResponse>")
    return combinedXml.toString()
    }
    */
}
