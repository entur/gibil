package org.example

import java.time.Instant
import org.example.netex.Airport

//Temporary function to test JAXB objects fetched and made from Avinor api data
fun parseAndPrintFlights(airportData: Airport) {

    try {
        println("Flyplass: ${airportData.name}")
        println("Sist oppdatert: ${airportData.flightsContainer?.lastUpdate ?: "N/A"}")

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

    val exampleQueryAPI = avinorApi.apiCall(
        airportCodeParam = chosenAirport,
        directionParam = "A",
        lastUpdateParam = specificTime,
        serviceTypeParam = "E",
        timeToParam = 336,
        timeFromParam = 24,
    )

    val xmlData = avinorApi.apiCall(chosenAirport, directionParam = "D", lastUpdateParam = Instant.now())
    if (xmlData != null) {
        parseAndPrintFlights(AVXH.unmarshall(xmlData))
    } else {
        println("Failed to fetch XML data")
    }

    Thread.sleep(3000) // Wait for async response */

}