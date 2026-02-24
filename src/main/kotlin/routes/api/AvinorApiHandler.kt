package routes.api

import jakarta.annotation.PostConstruct
import org.gibil.service.ApiService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import model.AvinorXmlFeedParams
import model.airportNames.AirportNames
import org.springframework.web.util.UriComponentsBuilder
import util.SharedJaxbContext
import java.io.StringReader

/**
 * Is the handler for XMLfeed- and airportcode-Api, and also handles converting java time instant-datetimes into correct timezone for user.
 */
@Component
class AvinorApiHandler(
    private val apiService: ApiService,
    @Value("\${avinor.api.base-url-xmlfeed}") private val baseUrlXmlFeed: String,
    @Value("\${avinor.api.base-url-airport-names}") private val baseUrlAirportNames: String
) {

    private var airportIATASet = emptySet<String>()

    @PostConstruct
    internal fun init() {
        refreshAirportNameSet()
    }

    /**
     * Makes call to Avinors airportNames api, unmarshalls XML return into [AirportNames].
     * Makes set of IATAS in the [airportIATASet]
     */
    private fun refreshAirportNameSet() {
        val xml = apiService.apiCall(baseUrlAirportNames) ?: return
        val unmarshaller = SharedJaxbContext.createUnmarshaller()
        val airportNames = unmarshaller.unmarshal(StringReader(xml)) as AirportNames
        airportIATASet = airportNames.airportName
            .mapNotNull { it.code?.uppercase() }
            .toSet()
    }

    /**
     * Validates an airport code against the cached set of known Avinor airport codes.
     * The set is populated once at startup via [refreshAirportNameSet].
     * @param airportCode String, a three letter IATA airport code
     * @return true if the airport code exists in Avinor's system
     */
    private fun airportCodeValidator(airportCode: String): Boolean {
        return airportCode.uppercase() in airportIATASet
    }

    /**
     * Builds a url string used for the AvinorXmlFeed Api
     * @param AvinorXmlFeedParams, makes use of [airportCode], [timeFrom], [timeTo], [direction]
     * @return finished AvinorXmlFeed url String
     */
    fun avinorXmlFeedUrlBuilder(params: AvinorXmlFeedParams): String {
        require(airportCodeValidator(params.airportCode)) {
            "Invalid airport code: ${params.airportCode}"
        }

        val builder = UriComponentsBuilder.fromUriString(baseUrlXmlFeed)
            .queryParam("airport", params.airportCode.uppercase())
            .queryParam("TimeFrom", params.timeFrom)
            .queryParam("TimeTo", params.timeTo)

        if(params.direction == "A" || params.direction == "D") {
            builder.queryParam("direction", params.direction)
        }

        return builder.build().toUriString()
    }
}