package org.gibil

import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedParamsLogic
import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedApiHandler
import org.gibil.service.ApiService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import service.FlightAggregationService
import siri.SiriETMapper
import siri.SiriETPublisher

@RestController
class Endpoint(
    private val avinorXmlFeedApiHandler: AvinorXmlFeedApiHandler,
    private val flightAggregationService: FlightAggregationService,
    private val siriETMapper: SiriETMapper,
    private val siriETPublisher: SiriETPublisher,
    private val apiService: ApiService
) {

    /**
     * SIRI-ET endpoint that aggregates data from ALL Avinor airports.
     * Merges departure and arrival data for complete EstimatedCalls.
     * Warning: Makes ~55 API calls, may take 30-60 seconds.
     */
    @GetMapping("/siri", produces = [MediaType.APPLICATION_XML_VALUE])
    fun siriAllAirportsEndpoint(): String {
    val unifiedFlights = flightAggregationService.fetchUnifiedFlights()
    val siri = siriETMapper.mapUnifiedFlightsToSiri(unifiedFlights)
    return siriETPublisher.toXml(siri)
    }


    /**
     * Debug endpoint that returns the raw XML response from the Avinor API.
     */
    @GetMapping("/avinor", produces = [MediaType.APPLICATION_XML_VALUE])
    fun rawAvinorEndpoint(
        @RequestParam(defaultValue = "OSL") airport: String
    ): String {
        val url = avinorXmlFeedApiHandler.avinorXmlFeedUrlBuilder(
            AvinorXmlFeedParamsLogic(airportCode = airport)
        )
        return apiService.apiCall(url) ?: "Error: No response from Avinor API"
    }

}