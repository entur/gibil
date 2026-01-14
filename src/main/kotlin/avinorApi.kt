package org.example

import okhttp3.OkHttpClient
import okhttp3.Request

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class AvinorApiHandling(){
    val client = OkHttpClient()
    var urlBuilderLink = ""

    public fun avinorXmlFeedApiCall(
        airportCodeParam: String,
        timeFromParam: Int? = 2,
        timeToParam: Int? = 7,
        directionParam: String? = null,
        lastUpdateParam: Instant? = null,
        serviceTypeParam: String? = null,
        codeshareParam: Boolean? = null
    ): String? {
        /*
        Handles the apicall to the avinor api, urlBuilder creates the url that is then used by the http3 package to fetch xml dataa from the api, it returns the raw xml as a string or an error message
         */

        val url = urlBuilder(airportCodeParam, timeFromParam, timeToParam, directionParam, lastUpdateParam, serviceTypeParam, codeshareParam)

        //if the response from the urlBuilder isn't an error-message
        if ("Error" !in url){
            return apiCall(url)
        } else {
            return "Error with avinor-XmlFeed api-call"
        }

    }

    private fun apiCall(url: String): String? {
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

    private fun urlBuilder(airportCodeParam: String,
                   timeFromParam: Int? = 1,
                   timeToParam: Int? = 7,
                   directionParam: String?,
                   lastUpdateParam: Instant? = null,
                   serviceTypeParam: String? = null,
                   codeshareParam: Boolean? = null ): String {
        /*
         Makes a complete url for the api to use based on the avinor api.
         Obligatory parameters: airport code, example: OSL
         non-obligatory parameters:
            timeFrom, what flights you fetch backwards in time, counts in hours
            timeTo, what flights you fetch forwards in time, counts in hours
            direction, shows either arrival flights, departure flights, or both. "A" shows only arrivals, "D" shows only departures, nothing will show both.
            lastUpdate, shows flightdata only from after a set datetime
            codeshare, adds possible codeshare information to the flightdata, consists of: codeshareAirlineDesignators, codeshareAirlineNames, codeshareFlightNumbers and codeshareOperationalSuffixs.
            serviceType, option to differentiate based on flight type, such as helicopter(E), regular flights(J), charter flights(C)
        */
        val baseurl = "https://asrv.avinor.no/XmlFeed/v1.0"

        urlBuilderLink = baseurl

        if (airportCodeCheckApi(airportCodeParam)) {
            val airport = "?airport=" + airportCodeParam.uppercase()
            urlBuilderLink += airport
        } else {
            throw IllegalArgumentException("Error: Airportcode not valid! XmlFeed api-call not made!")
            //return "Error: Airportcode not valid! XmlFeed api-call not made!"
        }

        //timeFromParam handling, minimum value is 1 and max is 36
        if (timeFromParam != null && timeFromParam <= 36 && timeFromParam >= 1) {
            val timeFrom = "&TimeFrom=" + timeFromParam
            urlBuilderLink += timeFrom
        } else if (timeFromParam != null) {
            throw IllegalArgumentException("TimeFrom parameter is outside of valid index, can only be between 1 and 36 hours, timeFrom set to default")
            //println("TimeFrom parameter is outside of valid index, can only be between 1 and 36 hours, timeFrom set to default")
        } else {
            //do nothing, not obligatory parameter for api
        }

        //timeToParam handling, minimum: 7 maximum 336
        if (timeToParam != null && timeToParam <= 336 && timeToParam >= 7) {
            val timeFrom = "&TimeTo=" + timeToParam
            urlBuilderLink += timeFrom
        } else if (timeToParam != null) {
            throw IllegalArgumentException("TimeTo parameter is outside of valid index, can only be between 7 and 336 hours, timeTo set to default")
            //println("TimeTo parameter is outside of valid index, can only be between 7 and 336 hours, timeTo set to default")
        } else {
            //do nothing, not obligatory parameter for api
        }

        //adds the optional "E" service type if the option is specified
        if (serviceTypeParam != null && (serviceTypeParam.uppercase() == "E")) {
            urlBuilderLink += "&serviceType=" + serviceTypeParam
        } else if (serviceTypeParam != null) {
            throw IllegalArgumentException("Servicetype not valid, input ignored")
            //println("Servicetype not valid, input ignored")
        } else {
            //do nothing, not obligatory parameter for api
        }

        //formats last update parameter
            //Accepted format: yyyy-MM-ddTHH:mm:ssZ
        if (lastUpdateParam != null) {
            //set format correctly - ISO-8601
            val lastUpdate = lastUpdateParam.toString()
            urlBuilderLink += "&lastUpdate" + lastUpdate
        } else {
            //do nothing, not obligatory parameter for api
        }

        //formats direction-information if a valid direction is specified, else sets it to be nothing
        if (directionParam != null && (directionParam.uppercase() == "D" || directionParam.uppercase() == "A")) {
            val direction = "&Direction=" + directionParam
            urlBuilderLink += direction
        } else if(directionParam != null) {
            throw IllegalArgumentException("Direction parameter invalid, input ignored")
            //println("Direction parameter invalid, input ignored")
        } else {
            //do nothing, not obligatory parameter for api
        }

        //adds the optional codeshare information
        if (codeshareParam != null && codeshareParam) {
            urlBuilderLink += "&codeshare=Y"
        } else {
            //do nothing, not obligatory parameter for api
        }

        return urlBuilderLink
    }

    private fun airportCodeCheckApi(airportCodeParam: String): Boolean{
        /*
        Uses airportNames api from avinor to check if the airportcode is in their db, and thus valid for their main api-call
        expected response from this api being used, when OSL is the param:
            <airportNames>
                <airportName code="OSL" name="Oslo"/>
            </airportNames>
         */

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

    public fun userCorrectDate(Datetime: String): String{
        /*
        Takes an incoming instant datetime string, parses it, and then converts it into the correct utc for the user
         */
        try {
            //makes string into time object
            val datetimeOriginal = Instant.parse(Datetime)

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