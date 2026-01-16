package org.example

import AirlineNameHandler
import java.time.Instant
import model.avinorApi.Airport
import java.io.File
import siri.SiriETMapper
import siri.SiriETPublisher
import org.entur.siri.validator.SiriValidator
import java.time.Clock

//Temporary function to test JAXB objects fetched and made from Avinor api data
fun parseAndPrintFlights(airportData: Airport) {

    try {
        println("Flyplass: ${airportData.name}")
        val avinorApiHandler = AvinorApiHandler()
        if (airportData.flightsContainer?.lastUpdate != null){
            val lastUpdate : String = airportData.flightsContainer?.lastUpdate !! //forces not null
            val userCorrectDate = avinorApiHandler.userCorrectDate(lastUpdate)
            println("Sist oppdatert: $userCorrectDate")
        }

        val cache = AirlineNameHandler()
        airportData.flightsContainer?.flight?.forEach { flight ->
            println("Fly: ${if(flight.airline != null){cache.getName(flight.airline !!)}else{}} with id; ${flight.flightId} to/from ${flight.airport} - Status: ${flight.status?.code ?: "N/A"}")
        }
    } catch (e: Exception) {
        println("Something went wrong while parsing: ${e.message}")
        e.printStackTrace()
    }
}

fun main() {
    val AVXH = AvinorScheduleXmlHandler()

    println("Please choose a airport")
    val chosenAirport = readln()
    val avinorApi = AvinorApiHandler()
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

    val cache = AirlineNameHandler()

    // Update from API once in a while
        //cache.update()

    // Use it
    println(cache.getName("AA"))  // American Airlines
    println(cache.isValid("BA"))  // true

    Thread.sleep(3000) // Wait for async response */

    val airportCode = chosenAirport
    val airport = AVXH.unmarshallXmlToAirport(xmlData ?: "")

    // Convert to SIRI-ET format
    println("Converting to SIRI-ET format...")
    val siriMapper = SiriETMapper()
    val siriPublisher = SiriETPublisher()
    val siri = siriMapper.mapToSiri(airport, airportCode)

    // Generate XML output
    val siriXml = siriPublisher.toXml(siri)

    // Save to file
    val outputFile = File("siri-et-output.xml")
    siriPublisher.toFile(siri, outputFile, formatOutput = true)
    println("SIRI-ET XML saved to: ${outputFile.absolutePath}")
    println()

    SiriValidator.validate(siriXml, SiriValidator.Version.VERSION_2_1)

    // Print sample of output
    println("=== SIRI-ET XML Output (first 2000 chars) ===")
    println(siriXml.take(2000))
    if (siriXml.length > 2000) {
        println("... (truncated, see ${outputFile.name} for full output)")
    }

}
