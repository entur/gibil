package siri

import model.avinorApi.Airport
import model.avinorApi.Flight
import model.avinorApi.FlightsContainer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.ZonedDateTime

class SiriETPublisherTest {

    @Test
    fun `should convert SIRI to XML string`() {
        val publisher = SiriETPublisher()
        val mapper = SiriETMapper()
        val siri = mapper.mapToSiri(createValidAirport("OSL"), "OSL")

        val xml = publisher.toXml(siri)

        assertNotNull(xml)
        assertTrue(xml.isNotEmpty())
        assertTrue(xml.contains("<?xml"))
    }

    @Test
    fun `should format XML with indentation`() {
        val publisher = SiriETPublisher()
        val mapper = SiriETMapper()
        val siri = mapper.mapToSiri(createValidAirport("OSL"), "OSL")

        val formattedXml = publisher.toXml(siri, formatOutput = true)
        val unformattedXml = publisher.toXml(siri, formatOutput = false)

        assertTrue(formattedXml.contains("\n"))
        assertTrue(formattedXml.length > unformattedXml.length)
    }

    @Test
    fun `should write SIRI to file`(@TempDir tempDir: File) {
        val publisher = SiriETPublisher()
        val mapper = SiriETMapper()
        val siri = mapper.mapToSiri(createValidAirport("OSL"), "OSL")
        val outputFile = File(tempDir, "output.xml")

        publisher.toFile(siri, outputFile, formatOutput = true)

        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 0)
    }

    @Test
    fun `should handle multiple flights`() {
        val publisher = SiriETPublisher()
        val mapper = SiriETMapper()
        val airport = createAirport("OSL", listOf(
            createValidFlight("SK4321", "SK"),
            createValidFlight("DY4322", "DY"),
            createValidFlight("WF4323", "WF")
        ))
        val siri = mapper.mapToSiri(airport, "OSL")

        val xml = publisher.toXml(siri)

        assertNotNull(xml)
        assertTrue(xml.isNotEmpty())
    }

    // Helper methods
    private fun createValidAirport(code: String) =
        createAirport(code, listOf(createValidFlight()))

    private fun createAirport(code: String, flights: List<Flight>) = Airport().apply {
        flightsContainer = FlightsContainer().apply {
            lastUpdate = ZonedDateTime.now().toString()
            flight = flights.toMutableList()
        }
    }

    private fun createValidFlight(
        flightId: String = "SK4321",
        airline: String = "SK"
    ) = Flight().apply {
        this.flightId = flightId
        this.airline = airline
        this.domInt = "D"
        this.arrDep = "D"
        this.scheduleTime = ZonedDateTime.now().toString()
        this.airport = "BGO"
        this.uniqueID = flightId.hashCode().toString()
    }
}
