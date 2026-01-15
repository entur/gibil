package org.example

import java.time.Instant
import org.example.netex.Airport
import java.time.Clock

//Temporary function to test JAXB objects fetched and made from Avinor api data
fun parseAndPrintFlights(airportData: Airport) {

    try {
        println("Flyplass: ${airportData.name}")
        val avinorApiHandling = AvinorApiHandling()
        if (airportData.flightsContainer?.lastUpdate != null){
            val lastUpdate : String = airportData.flightsContainer?.lastUpdate !! //forces not null
            val userCorrectDate = avinorApiHandling.userCorrectDate(lastUpdate)
            println("Sist oppdatert: $userCorrectDate")
        }

        airportData.flightsContainer?.flight?.forEach { flight ->
            println("Fly: ${flight.flightId} to/from ${flight.airport} - Status: ${flight.status?.code ?: "N/A"}")
        }
    } catch (e: Exception) {
        println("Something went wrong while parsing: ${e.message}")
        e.printStackTrace()
    }
}

fun main() {
    var AVXH = AvinorScheduleXmlHandler()

    println("Please choose a airport")
    val chosenAirport = readln()
    val avinorApi = AvinorApiHandling()
    val specificTime = Instant.parse("2024-08-08T09:30:00Z")

    val exampleQueryAPI = avinorApi.avinorXmlFeedApiCall(
        airportCodeParam = chosenAirport,
        directionParam = "A",
        lastUpdateParam = specificTime,
        serviceTypeParam = "E",
        timeToParam = 336,
        timeFromParam = 24,
        codeshareParam = true
    )
    val clock = Clock.systemUTC();
    val xmlData = avinorApi.avinorXmlFeedApiCall(chosenAirport, directionParam = "D", lastUpdateParam = Instant.now(clock), codeshareParam = true)
    if (xmlData != null && "Error" !in xmlData) {
        parseAndPrintFlights(AVXH.unmarshallXmlToAirport(xmlData))
    } else {
        println("Failed to fetch XML data: ($xmlData)")
    }

    Thread.sleep(3000) // Wait for async response */

}