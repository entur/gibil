package org.example

import okhttp3.OkHttpClient
import okhttp3.Request

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.text.uppercase

const val TIMEFROMPARAM_MIN_NUM = 1
const val TIMEFROMPARAM_MAX_NUM = 36

const val TIMETOPARAM_MIN_NUM = 7
const val TIMETOPARAM_MAX_NUM = 336

/**
 * Is the handler for XMLfeed- and airportcode-Api, and also handles converting java time instant-datetimes into correct timezone for user.
 *
 */
class AvinorApiHandler(){
    val client = OkHttpClient()
    var urlBuilderLink = ""
    var timeFrom: Int = 2
    var timeTo: Int = 7
    var direction: String? = null
    var lastUpdate: Instant? = null
    var serviceType: String? = null
    var codeshare: Boolean? = null
    /**
     * Handles the apicall to the avinor api, urlBuilder creates the url that is then used by the http3 package to fetch XML dataa from the api, it returns the raw XML as a string or an error message
     *
     * @param airportCodeParam OBLIGATORY code of airport, example; OSL, BGO
     * @param timeFromParam optional amount of hours worth of flight data before last updated time
     * @param timeToParam optional amount of hours worth of flight data after last updated time
     * @param directionParam optional, choose to fetch "A" or "D", or both, A is Arrival flights only, D is Departure flights only, picking no option shows both
     * @param lastUpdateParam optional date of when flight data range is gathered from, standard is now
     * @param serviceTypeParam optional, choose to add helicopter flight information, only valid option is "E"
     * @param codeshareParam optional, choose to add codeshare information or not, consists of: codeshareAirlineDesignators, codeshareAirlineNames, codeshareFlightNumbers and codeshareOperationalSuffixs.
     * @return XML-result from XMLfeed api-call or an errormessage if failure occured
     */
    public fun avinorXmlFeedApiCall(
        airportCodeParam: String,
        timeFromParam: Int? = null,
        timeToParam: Int? = null,
        directionParam: String? = null,
        lastUpdateParam: Instant? = null,
        serviceTypeParam: String? = null,
        codeshareParam: Boolean? = null
    ): String? {
        println("asd")
        //sets the fields to be the parameters, if the parameters are set. this is done since theese parameters are optional
        timeFromParam?.let { timeFrom = it }
        timeToParam?.let { timeTo = it }
        directionParam?.let { direction = directionParam.uppercase() }
        lastUpdateParam?.let { lastUpdate = it }
        serviceTypeParam?.let { serviceType = serviceTypeParam.uppercase() }
        codeshareParam?.let { codeshare = it }

        val url = urlBuilder(airportCodeParam)

        //if the response from the urlBuilder isn't an error-message
        if ("Error" !in url){
            return apiCall(url)
        } else {
            return "Error with avinor-XmlFeed api-call"
        }

    }

    /**
     * A basic api call that returns the raw XML it gets from the call.
     * Works only on open(public) level api's
     * @param url the complete url which the api-call is based on
     */
    public fun apiCall(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()

        response.use {
            return if (response.isSuccessful) {
                response.body?.string()  // Returns raw XML
            } else {
                println("Error: ${response.code}")
                null
            }
        }
    }

    /**
     * Makes a complete url for the api to use based on the avinor api.
     * Only adds option to url string if the option is chosen or if the option is obligatory
     * @param airportCodeParam the code of the airport which the api-information is going to fetch information from
     * @return returns a string which is the complete url for the xmlfeed api call
     */
    private fun urlBuilder(airportCodeParam: String): String {
        val baseurl = "https://asrv.avinor.no/XmlFeed/v1.0"

        urlBuilderLink = baseurl

        //checks if the airportcode is valid using the airportCodeCheckApi method
        if (airportCodeCheckApi(airportCodeParam)) {
            urlBuilderLink += "?airport=${airportCodeParam.uppercase()}"
        } else {
            throw IllegalArgumentException("Error: Airportcode not valid! XmlFeed api-call not made!")
        }

        //timeFromParam handling, minimum value is 1 and max is 36
        if (timeFrom <= TIMEFROMPARAM_MAX_NUM && timeFrom >= TIMEFROMPARAM_MIN_NUM) {
            urlBuilderLink += "&TimeFrom=$timeFrom"
        } else if (timeFrom != 2) {
            throw IllegalArgumentException("TimeFrom parameter is outside of valid index, can only be between 1 and 36 hours, timeFrom set to default")
        } else {
            //do nothing, not obligatory parameter for api
        }

        //timeToParam handling, minimum: 7 maximum 336
        if (timeTo <= TIMETOPARAM_MAX_NUM && timeTo >= TIMETOPARAM_MIN_NUM) {
            urlBuilderLink += "&TimeTo=$timeTo"
        } else if (timeTo != 7) {
            throw IllegalArgumentException("TimeTo parameter is outside of valid index, can only be between 7 and 336 hours, timeTo set to default")
        } else {
            //do nothing, not obligatory parameter for api
        }

        //adds the optional "E" service type if the option is specified
        if (serviceType != null && (serviceType == "E")) {
            urlBuilderLink += "&serviceType=$serviceType"
        } else if (serviceType != null) {
            throw IllegalArgumentException("Servicetype not valid, input ignored")
        } else {
            //do nothing, not obligatory parameter for api
        }

        //formats last update parameter
            //Accepted format: yyyy-MM-ddTHH:mm:ssZ
        if (lastUpdate != null) {
            //set format correctly - ISO-8601
            val lastUpdateString = lastUpdate.toString()
            urlBuilderLink += "&lastUpdate$lastUpdateString"
        } else {
            //do nothing, not obligatory parameter for api
        }

        //formats direction-information if a valid direction is specified, else sets it to be nothing
        if (direction != null && (direction == "D" || direction == "A")) {
            urlBuilderLink += "&Direction$direction"
        } else if(direction != null) {
            throw IllegalArgumentException("Direction parameter invalid, input ignored")
        } else {
            //do nothing, not obligatory parameter for api
        }

        //adds the optional codeshare information
        if (codeshare != null) {
            if (codeshare !!) {
                urlBuilderLink += "&codeshare=Y"
            } else{
                //do nothing
            }
        } else {
            //do nothing, not obligatory parameter for api
        }

        return urlBuilderLink
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
    private fun airportCodeCheckApi(airportCodeParam: String): Boolean{
        val url = "https://asrv.avinor.no/airportNames/v1.0?airport=" + airportCodeParam.uppercase()

        //calls the api
        val response = apiCall(url)

        //a snippet of what's expected in the api-response
        val expectedInResponse = "code=\"${airportCodeParam.uppercase()}\""

        //if there's a respose, the airportcode was found by the api, and the airportcodeparameter is 3 characters
        if (response != null && airportCodeParam.length == 3 && expectedInResponse in response){
            return true
        } else {
            return false
        }
    }

    /**
     * Takes an incoming instant datetime string, parses it, and then converts it into the correct utc for the user
     * @param Datetime is a datetime of java.instant format with timezone information
     * @return corrected datetime information, sets the utc to be the same as the user has. Returns errormessage if format is invalid
     */
    public fun userCorrectDate(datetime: String): String{
        try {
            //makes string into time object
            val datetimeOriginal = Instant.parse(datetime)

            //finds user's local timezone and applies to original datetime
            val datetimeUserCorrect = datetimeOriginal.atZone(ZoneId.systemDefault())

            //formats for output
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val displayTime = datetimeUserCorrect.format(formatter)

            return displayTime
        } catch (e: Exception) {
            return "Error: Date format invalid; ${e.localizedMessage}"
        }
    }
}