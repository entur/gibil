package routes.api

import org.gibil.service.ApiService
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import org.gibil.AvinorApiConfig
import model.AvinorXmlFeedParams
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * Is the handler for XMLfeed- and airportcode-Api, and also handles converting java time instant-datetimes into correct timezone for user.
 */
@Component
open class AvinorApiHandler(private val apiService: ApiService) {

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    /**
     * Builds a url string used for the AvinorXmlFeed Api
     * @param AvinorXmlFeedParams, makes use of [airportCode], [timeFrom], [timeTo], [direction]
     * @return finished AvinorXmlFeed url String
     */
    open fun avinorXmlFeedUrlBuilder(params: AvinorXmlFeedParams): String {
        require(airportCodeValidator(params.airportCode)) {
            "Invalid airport code: ${params.airportCode}"
        }

        val builder = UriComponentsBuilder.fromUriString(AvinorApiConfig.BASE_URL_AVINOR_XMLFEED)
            .queryParam("airport", params.airportCode.uppercase())
            .queryParam("TimeFrom", params.timeFrom)
            .queryParam("TimeTo", params.timeTo)

        if(params.direction == "A" || params.direction == "D") {
            builder.queryParam("direction", params.direction)
        }

        return builder.build().toUriString()
    }


    /**
     * Uses airportNames api from avinor to check if the airportcode is in their db, and thus valid for their main api-call
     *  expected response from this api being used, when OSL is the param:
     *  <airportNames>
     *      <airportName code="OSL" name="Oslo"/>
     *  </airportNames>
     *  @param airportCodeParam a three letter string of the airportcode
     *  @return returns a true if the airportcode is found in the api-call, returns a false if it isn't
     */
    private fun airportCodeValidator(airportCode: String): Boolean {

        if(airportCode.length != 3) return false

        val upperCode = airportCode.uppercase()
        val url = "${AvinorApiConfig.BASE_URL_AVINOR_AIRPORT_NAMES}?airport=${upperCode}"

        //calls the api
        val response = apiService.apiCall(url)

        //a snippet of what's expected in the api-response
        val expectedInResponse = "code=\"${upperCode}\""

        return response != null && expectedInResponse in response
    }

    /**
     * Takes an incoming instant datetime string, parses it, and then converts it into the correct utc for the user
     * @param datetime is a datetime of java.instant format with timezone information
     * @return corrected datetime information, sets the utc to be the same as the user has. Returns error message if format is invalid
     */
    fun userCorrectDate(datetime: String): String{
        try {
            //makes string into time object
            // Try parsing as ZonedDateTime first, fall back to Instant
            val datetimeOriginal = try {
                ZonedDateTime.parse(datetime).toInstant()
            } catch (e: Exception) {
                Instant.parse(datetime)
            }

            //finds user's local timezone and applies to original datetime
            val datetimeUserCorrect = datetimeOriginal.atZone(ZoneId.systemDefault())

            return datetimeUserCorrect.format(DATE_TIME_FORMATTER)
        } catch (e: Exception) {
            return "Error: Date format in '$datetime' invalid; ${e.localizedMessage}"
        }
    }
}