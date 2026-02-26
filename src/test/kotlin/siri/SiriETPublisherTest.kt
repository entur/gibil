package siri

import model.FlightStop
import model.UnifiedFlight
import org.gibil.service.AirportQuayService
import io.mockk.every
import io.mockk.mockk
import service.FindServiceJourneyService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class SiriETPublisherTest {

    private val airportQuayService = mockk<AirportQuayService> {
        every { getQuayId(any()) } returns null
    }
    private val findServiceJourneyService = mockk<FindServiceJourneyService>(relaxed = true)

    @Test
    fun `should convert SIRI to XML string`() {
        val publisher = SiriETPublisher()
        val mapper = SiriETMapper(airportQuayService, findServiceJourneyService)
        val siri = mapper.mapUnifiedFlightsToSiri(listOf(createFlight()))

        val xml = publisher.toXml(siri)

        assertNotNull(xml)
        assertTrue(xml.isNotEmpty())
        assertTrue(xml.contains("<?xml"))
    }

    @Test
    fun `should format XML with indentation`() {
        val publisher = SiriETPublisher()
        val mapper = SiriETMapper(airportQuayService, findServiceJourneyService)
        val siri = mapper.mapUnifiedFlightsToSiri(listOf(createFlight()))

        val formattedXml = publisher.toXml(siri, formatOutput = true)
        val unformattedXml = publisher.toXml(siri, formatOutput = false)

        assertTrue(formattedXml.contains("\n"))
        assertTrue(formattedXml.length > unformattedXml.length)
    }

    @Test
    fun `should write SIRI to file`(@TempDir tempDir: File) {
        val publisher = SiriETPublisher()
        val mapper = SiriETMapper(airportQuayService, findServiceJourneyService)
        val siri = mapper.mapUnifiedFlightsToSiri(listOf(createFlight()))
        val outputFile = File(tempDir, "output.xml")

        publisher.toFile(siri, outputFile, formatOutput = true)

        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 0)
    }

    @Test
    fun `should handle multiple flights`() {
        val publisher = SiriETPublisher()
        val mapper = SiriETMapper(airportQuayService, findServiceJourneyService)
        val flights = listOf(
            createFlight("SK4321", "SK"),
            createFlight("DY4322", "DY"),
            createFlight("WF4323", "WF")
        )
        val siri = mapper.mapUnifiedFlightsToSiri(flights)

        val xml = publisher.toXml(siri)

        assertNotNull(xml)
        assertTrue(xml.isNotEmpty())
    }

    private fun createFlight(
        flightId: String = "SK4321",
        operator: String = "SK",
        origin: String = "OSL",
        destination: String = "BGO"
    ) = UnifiedFlight(
        flightId = flightId,
        operator = operator,
        date = LocalDate.now(),
        stops = listOf(
            FlightStop(airportCode = origin, arrivalTime = null, departureTime = LocalDateTime.now()),
            FlightStop(airportCode = destination, arrivalTime = LocalDateTime.now().plusHours(1), departureTime = null)
        )
    )
}
