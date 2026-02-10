package routes.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime

object AvinorApiConfig {
    const val TIME_FROM_MIN_NUM = 1
    const val TIME_FROM_MAX_NUM = 36
    const val TIME_FROM_DEFAULT = 2

    const val TIME_TO_MIN_NUM = 7
    const val TIME_TO_MAX_NUM = 336
    const val TIME_TO_DEFAULT = 10

    const val BASE_URL_AVINOR_XMLFEED = "https://asrv.avinor.no/XmlFeed/v1.0"
    const val BASE_URL_AVINOR_AIRPORT_NAMES = "https://asrv.avinor.no/airportNames/v1.0"
}

data class AvinorXmlFeedParams(
    val airportCode: String,
    val timeFrom: Int = AvinorApiConfig.TIME_FROM_DEFAULT,
    val timeTo: Int = AvinorApiConfig.TIME_TO_DEFAULT,
    val direction: String? = null
    //val lastUpdate: Instant? = null, Commented out due to not being in use for now
    //val codeshare: Boolean = false Commented out due to not being in use for now
)

/**
 * Is the handler for XMLfeed- and airportcode-Api, and also handles converting java time instant-datetimes into correct timezone for user.
 */
@Component
open class AvinorApiHandler(private val client: OkHttpClient = OkHttpClient()) {

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    /**
     * Builds a url string used for the AvinorXmlFeed Api
     * @param AvinorXmlFeedParams, makes use of [airportCode], [timeFrom], [timeTo], [direction]
     * @return finished AvinorXmlFeed url String
     */
    open fun avinorXmlFeedUrlBuilder(params: AvinorXmlFeedParams): String = buildString {
        append(AvinorApiConfig.BASE_URL_AVINOR_XMLFEED)

        require(airportCodeValidator(params.airportCode)) {
            "Invalid airport code: ${params.airportCode}"
        }
        append("?airport=${params.airportCode.uppercase()}")
        if(timeParamValidation(params)) {
            append("&TimeFrom=${params.timeFrom}")
            append("&TimeTo=${params.timeTo}")
        }
        if(params.direction == "A" || params.direction == "D") {
            append("&direction=${params.direction}")
        }
    }

    /**
     * Validates if the timeTo and timeFrom are within valid parameters, throws IllegalArgumentException if not
     * @param AvinorXmlFeedParams makes use of timeTo and timeFrom
     * @return boolean, returns true if no exception is thrown
     */
    private fun timeParamValidation(params: AvinorXmlFeedParams): Boolean {
        require(!(params.timeTo !in AvinorApiConfig.TIME_TO_MIN_NUM..AvinorApiConfig.TIME_TO_MAX_NUM)) {
            "TimeTo parameter is outside of valid range, can only be between 7 and 336 hours"
        }
        require(!(params.timeFrom !in AvinorApiConfig.TIME_FROM_MIN_NUM..AvinorApiConfig.TIME_FROM_MAX_NUM)) {
            "TimeFrom parameter is outside of valid range, can only be between 1 and 36 hours"
        }
        return true
    }

    /**
     * A basic api call that returns the raw XML it gets from the call.
     * Works only on open(public) level api's
     * @param url the complete url which the api-call is based on
     */
     open fun apiCall(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()

        response.use {
            return if (response.isSuccessful) {
                response.body?.string()  // Returns raw XML
            } else {
                throw IOException("Error: ${response.code}")
            }
        }
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
        val response = apiCall(url)

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