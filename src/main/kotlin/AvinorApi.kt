package org.example

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class avinorApiHandling(){
    val client = OkHttpClient()
    var urlBuilderLink = ""

    public fun apiCall(
        airportCodeParam: String,
        timeFromParam: Int? = 2,
        timeToParam: Int? = 7,
        directionParam: String? = null,
        lastUpdateParam: String? = null,
        serviceTypeParam: String? = null
    ): String? {
        /*
        Handles the apicall to the avinor api, urlBuilder creates the url that is then used by the http3 package to fetch xml dataa from the api, it returns the raw xml as a string or an error message
         */
        val url = urlBuilder(airportCodeParam, timeFromParam, timeToParam, directionParam, lastUpdateParam, serviceTypeParam)

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
                   lastUpdateParam: String? = "2024-08-08T09:30:00Z",
                   serviceTypeParam: String? = null): String {
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

        //airport handling todo:
        /*
        if (airport in xyz) {
            val airport = "?airport=" + airportCodeParam
            url += airport
        } else {
            println("Airport code not valid")
        }
         */
        val airport = "?airport=" + airportCodeParam
        urlBuilderLink += airport

        if (timeFromParam != null && timeFromParam <= 36 && timeFromParam >= 1) {
            val timeFrom = "&TimeFrom=" + timeFromParam
            urlBuilderLink += timeFrom
        } else if (timeFromParam != null) {
            println("TimeFrom parameter is outside of valid index, can only be between 1 and 36 hours")
        } else {
            println("Something went wrong, timeFromParam handling")
        }

        if (timeToParam != null && timeToParam <= 336 && timeToParam >= 7) {
            val timeFrom = "&TimeTo=" + timeToParam
            urlBuilderLink += timeFrom
        } else if (timeToParam != null) {
            println("TimeTo parameter is outside of valid index, can only be between 7 and 336 hours")
        } else {
            println("Something went wrong, timeToParam handling")
        }

        //adds the optional "E" service type if the option is specified
        if (serviceTypeParam != null && (serviceTypeParam == "E")) {
            urlBuilderLink += "&serviceType=" + serviceTypeParam
        } else if (serviceTypeParam != null) {
            println("Servicetype not valid")
        } else {
            //do nothing
        }

        //formats last update parameter
        /*todo: date handling
        //Accepted format: yyyy-MM-ddTHH:mm:ssZ
        if (Dateobject is set){
            //set format correctly
            val lastUpdate =
            url += "&lastUpdate" + lastUpdate
        } else {
            //do nothing
        }
         */
        val lastUpdate = "&lastUpdate=" + lastUpdateParam

        //formats direction-information if a valid direction is specified, else sets it to be nothing
        if (directionParam != null && (directionParam == "D" || directionParam == "A")) {
            val direction = "&Direction=" + directionParam
            urlBuilderLink += direction
        } else {
            //do nothing
        }


        return urlBuilderLink
    }

}