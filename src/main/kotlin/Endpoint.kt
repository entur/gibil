package org.gibil

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import service.FlightAggregationService
import service.ServiceJourneyResolver
import siri.SiriETMapper
import siri.SiriETPublisher

@RestController
class Endpoint(
    private val flightAggregationService: FlightAggregationService,
    private val serviceJourneyResolver: ServiceJourneyResolver,
    private val siriETMapper: SiriETMapper,
    private val siriETPublisher: SiriETPublisher
) {

    /**
     * SIRI-ET endpoint that aggregates data from ALL Avinor airports.
     * Merges departure and arrival data for complete EstimatedCalls.
     * Warning: Makes ~55 API calls, may take 30-60 seconds.
     */
    @GetMapping("/siri", produces = [MediaType.APPLICATION_XML_VALUE])
    fun siriAllAirportsEndpoint(): String {
        val unifiedFlights = flightAggregationService.fetchUnifiedFlights()
        val resolved = serviceJourneyResolver.resolve(unifiedFlights)
        val siri = siriETMapper.mapUnifiedFlightsToSiri(resolved)
        return siriETPublisher.toXml(siri)
    }
}