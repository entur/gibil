package util

import io.mockk.every
import io.mockk.mockk
import model.FlightStop
import model.UnifiedFlight
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import service.AirportQuayService
import siri.SiriETMapper
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class XmlUtilTest {
    private val airportQuayService = mockk<AirportQuayService> {
        every { getQuayId(any()) } returns null
    }

    @Test
    fun `should convert SIRI to XML string`() {
        val mapper = SiriETMapper(airportQuayService)
        val siri = mapper.mapUnifiedFlightsToSiri(listOf(createFlight()))

        val xml = XmlUtil.toXml(siri)

        assertNotNull(xml)
        assertTrue(xml.isNotEmpty())
        assertTrue(xml.contains("<?xml"))
    }

    @Test
    fun `should format XML with indentation`() {
        val mapper = SiriETMapper(airportQuayService)
        val siri = mapper.mapUnifiedFlightsToSiri(listOf(createFlight()))

        val formattedXml = XmlUtil.toXml(siri, formatOutput = true)
        val unformattedXml = XmlUtil.toXml(siri, formatOutput = false)

        assertTrue(formattedXml.contains("\n"))
        assertTrue(formattedXml.length > unformattedXml.length)
    }

    @Test
    fun `should handle multiple flights`() {
        val mapper = SiriETMapper(airportQuayService)
        val flights = listOf(
            createFlight("SK4321", "SK"),
            createFlight("DY4322", "DY"),
            createFlight("WF4323", "WF")
        )
        val siri = mapper.mapUnifiedFlightsToSiri(flights)

        val xml = XmlUtil.toXml(siri)

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
        serviceJourneyRef = "AVI:ServiceJourney:${flightId}_hash",
        stops = listOf(
            FlightStop(airportCode = origin, arrivalTime = null, departureTime = Instant.now()),
            FlightStop(airportCode = destination, arrivalTime = Instant.now().plus(1, ChronoUnit.HOURS), departureTime = null)
        )
    )
}