package controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import service.FlightAggregationService
import service.serviceJourney.ServiceJourneyResolver
import siri.SiriETMapper
import util.XmlUtil

@RestController
class SiriEtDebugController(
    private val flightAggregationService: FlightAggregationService,
    private val serviceJourneyResolver: ServiceJourneyResolver,
    private val siriETMapper: SiriETMapper,
) {

    /**
     * SIRI-ET endpoint that aggregates data from ALL Avinor airports.
     * Merges departure and arrival data for complete EstimatedCalls.
     * Warning: Makes ~55 API calls, may take 30-60 seconds.
     */
    @GetMapping("/siri", produces = [MediaType.APPLICATION_XML_VALUE])
    fun siriAllAirportsEndpoint(): String {
        val unifiedFlights = flightAggregationService.buildUnifiedFlights()
        val resolved = serviceJourneyResolver.resolve(unifiedFlights)
        val siri = siriETMapper.mapUnifiedFlightsToSiri(resolved)
        return XmlUtil.toXml(siri)
    }
}