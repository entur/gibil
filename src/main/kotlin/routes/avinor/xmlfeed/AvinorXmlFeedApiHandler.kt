package org.gibil.routes.avinor.xmlfeed

import jakarta.annotation.PostConstruct
import org.gibil.routes.avinor.airportname.AvinorAirportNamesApiHandler
import org.gibil.service.ApiService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * Is the handler for XMLfeed- and airportcode-Api, and also handles converting java time instant-datetimes into correct timezone for user.
 */
@Component
class AvinorXmlFeedApiHandler(
    private val airportNameHandler: AvinorAirportNamesApiHandler,
    @Value("\${avinor.api.base-url-xmlfeed}") private val baseUrlXmlFeed: String
) {

    /**
     * Builds a url string used for the AvinorXmlFeed Api
     * @param AvinorXmlFeedParamsLogic, makes use of [airportCode], [timeFrom], [timeTo], [direction]
     * @return finished AvinorXmlFeed url String
     */
    fun avinorXmlFeedUrlBuilder(params: AvinorXmlFeedParamsLogic): String {
        require(airportNameHandler.airportCodeValidator(params.airportCode)) {
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