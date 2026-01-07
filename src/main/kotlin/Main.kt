package org.example


import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import java.util.Date


//imports date-handling, dosent work for now for some mysterious reasons, i gave up:D
//import kotlinx.datetime.LocalDate
//import kotlinx.datetime.format.*

fun urlBuilder(airportCodeParam: String, timeFromParam: String = "0", timeToParam: String = "1", directionParam: String = "", lastUpdateParam: String = "2024-08-08T09:30:00Z", serviceTypeParam: String = ""): String {
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
    val airport = "?airport=" + airportCodeParam
    val time = "&TimeFrom=" + timeFromParam + "&TimeTo=" + timeToParam

    //formats direction-information if a valid direction is specified, else sets it to be nothing
    val serviceType = if (serviceTypeParam != "" && (serviceTypeParam == "E" || serviceTypeParam == "J" || serviceTypeParam == "C")) {
        "&serviceType=" + serviceTypeParam
    } else {
        ""
    }

    //formats last update parameter
    val lastUpdate = "&lastUpdate=" + lastUpdateParam

    //formats direction-information if a valid direction is specified, else sets it to be nothing
    val direction = if (directionParam != "" && (directionParam == "D" || directionParam == "A")) {
        "&Direction=" + directionParam
    } else {
        ""
    }


    val url = baseurl + airport + time + direction + lastUpdate
    return url;
}


fun main() {
    val client = OkHttpClient()

    val exampleQueryAPI = urlBuilder(
        airportCodeParam="OSL",
        directionParam="A",
        lastUpdateParam = "2026-01-01T09:30:00Z",
        serviceTypeParam="E"
    )

    val request = Request.Builder()
        .url(exampleQueryAPI)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("Request failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (response.isSuccessful) {
                    println("Response: ${response.body?.string()}")
                } else {
                    println("Error: ${response.code}")
                }
            }
        }
    })

    Thread.sleep(3000) // Wait for async response
}