package service

import handler.AvinorScheduleXmlHandler
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedParamsLogic
import model.xmlFeedApi.Airport
import model.xmlFeedApi.Flight
import model.xmlFeedApi.FlightStatus
import model.xmlFeedApi.FlightsContainer
import org.gibil.service.ApiService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedApiHandler
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FlightAggregationServiceTest {

    private lateinit var avinorXmlFeedApiHandler: AvinorXmlFeedApiHandler
    private lateinit var xmlHandler: AvinorScheduleXmlHandler
    private lateinit var apiService: ApiService
    private lateinit var flightAggregationService: FlightAggregationService
    private lateinit var ioDispatcher: CoroutineDispatcher

    @BeforeEach
    fun init() {
        avinorXmlFeedApiHandler = mockk()
        xmlHandler = mockk()
        apiService = mockk()
        ioDispatcher = Dispatchers.Unconfined

        flightAggregationService = FlightAggregationService(avinorXmlFeedApiHandler, xmlHandler, apiService, ioDispatcher)
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Nested
    inner class FetchAndMergeAllFlights {

        @Test
        fun `should merge departure and arrival data from different airports`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            // Setup: Flight OSL -> BGO departing in 2 hours
            val oslFlight = createFlight(
                uniqueID = "12345",
                flightId = "DY123",
                airline = "DY",
                arrDep = "D",
                airport = "BGO",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "D"
            )

            val bgoFlight = createFlight(
                uniqueID = "12345",
                flightId = "DY123",
                airline = "DY",
                arrDep = "A",
                airport = "OSL",
                scheduleTime = now.plusHours(3).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "D"
            )

            mockAirportData("OSL", listOf(oslFlight))
            mockAirportData("BGO", listOf(bgoFlight))
            mockAirportsList(listOf("OSL", "BGO"))

            val result = flightAggregationService.fetchAndMergeAllFlights()

            assertEquals(1, result.size)
            val mergedFlight = result["12345"]!!
            assertEquals("OSL", mergedFlight.departureAirport)
            assertEquals("BGO", mergedFlight.arrivalAirport)
            assertEquals(now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME), mergedFlight.scheduledDepartureTime)
            assertEquals(now.plusHours(3).format(DateTimeFormatter.ISO_DATE_TIME), mergedFlight.scheduledArrivalTime)
            assertTrue(mergedFlight.isMerged)
        }

        @Test
        fun `should filter out international flights`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            val domesticFlight = createFlight(
                uniqueID = "12345",
                flightId = "DY123",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "D"
            )

            val internationalFlight = createFlight(
                uniqueID = "67890",
                flightId = "DY456",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "I"
            )

            mockAirportData("OSL", listOf(domesticFlight, internationalFlight))
            mockAirportsList(listOf("OSL"))

            val result = flightAggregationService.fetchAndMergeAllFlights()

            assertEquals(1, result.size)
            assertTrue(result.containsKey("12345"))
            assertFalse(result.containsKey("67890"))
        }

        @Test
        fun `should filter out flights with departure outside time window`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            // Flight departed 30 minutes ago (outside 20 min window)
            val oldFlight = createFlight(
                uniqueID = "12345",
                flightId = "DY123",
                arrDep = "D",
                airport = "BGO",
                scheduleTime = now.minusMinutes(30).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "D"
            )

            mockAirportData("OSL", listOf(oldFlight))
            mockAirportsList(listOf("OSL"))

            val result = flightAggregationService.fetchAndMergeAllFlights()

            assertEquals(0, result.size)
        }

        @Test
        fun `should filter out flights with arrival outside time window`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            // Flight arrives 8 hours in future (outside 7 hour window)
            val futureFlight = createFlight(
                uniqueID = "12345",
                flightId = "DY123",
                arrDep = "A",
                airport = "OSL",
                scheduleTime = now.plusHours(8).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "D"
            )

            mockAirportData("BGO", listOf(futureFlight))
            mockAirportsList(listOf("BGO"))

            val result = flightAggregationService.fetchAndMergeAllFlights()

            assertEquals(0, result.size)
        }

        @Test
        fun `should keep flights within time window`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            // Flight departs in 3 hours (within window)
            val validFlight = createFlight(
                uniqueID = "12345",
                flightId = "DY123",
                arrDep = "D",
                airport = "BGO",
                scheduleTime = now.plusHours(3).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "D"
            )

            mockAirportData("OSL", listOf(validFlight))
            mockAirportsList(listOf("OSL"))

            val result = flightAggregationService.fetchAndMergeAllFlights()

            assertEquals(1, result.size)
        }

        @Test
        fun `should handle API errors gracefully`() = runBlocking {
            every { avinorXmlFeedApiHandler.avinorXmlFeedUrlBuilder(any()) } returns "http://test.url"
            every { apiService.apiCall(any()) } returns "Error: API unavailable"
            mockAirportsList(listOf("OSL"))

            val result = flightAggregationService.fetchAndMergeAllFlights()

            assertEquals(0, result.size)
        }

        @Test
        fun `should handle null API response gracefully`() = runBlocking {
            every { avinorXmlFeedApiHandler.avinorXmlFeedUrlBuilder(any()) } returns "http://test.url"
            every { apiService.apiCall(any()) } returns null
            mockAirportsList(listOf("OSL"))

            val result = flightAggregationService.fetchAndMergeAllFlights()

            assertEquals(0, result.size)
        }
    }

    // Helper functions

    private fun createFlight(
        uniqueID: String,
        flightId: String,
        airline: String = "DY",
        arrDep: String = "D",
        airport: String = "OSL",
        viaAirport: String? = null,
        scheduleTime: String? = ZonedDateTime.now(ZoneOffset.UTC)
            .plusHours(2)
            .format(DateTimeFormatter.ISO_DATE_TIME),
        domInt: String = "D",
        statusCode: String = "N"
    ): Flight {
        return Flight().apply {
            this.uniqueID = uniqueID
            this.flightId = flightId
            this.airline = airline
            this.arrDep = arrDep
            this.airport = airport
            this.viaAirport = viaAirport
            this.scheduleTime = scheduleTime
            this.domInt = domInt
            this.status = FlightStatus().apply {
                this.code = statusCode
                this.time = scheduleTime
            }
        }
    }

    private fun mockAirportData(airportCode: String, flights: List<Flight>) {
        val airport = Airport().apply {
            flightsContainer = FlightsContainer().apply {
                flight = flights.toMutableList()
                lastUpdate = "2026-02-13T12:00:00Z"
            }
        }

        // Match any AvinorXmlFeedParams with the correct airportCode
        every {
            avinorXmlFeedApiHandler.avinorXmlFeedUrlBuilder(
                match<AvinorXmlFeedParamsLogic> { it.airportCode == airportCode }
            )
        } returns "http://test.url/$airportCode"

        every { apiService.apiCall("http://test.url/$airportCode") } returns "<xml>$airportCode</xml>"
        every { xmlHandler.unmarshallXmlToAirport("<xml>$airportCode</xml>") } returns airport
    }

    private fun mockAirportsList(airports: List<String>) {
        val airportsText = airports.joinToString("\n")

        mockkConstructor(ClassPathResource::class)
        every {
            anyConstructed<ClassPathResource>().inputStream
        } returns airportsText.byteInputStream()
    }

    @Nested
    inner class FetchUnifiedFlights {

        @Test
        fun `should stitch direct flight into 2 stops`() = runBlocking {
            val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)

            val oslDep = createFlight(
                uniqueID = "1", flightId = "DY123", airline = "DY",
                arrDep = "D", airport = "BGO",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val bgoArr = createFlight(
                uniqueID = "2", flightId = "DY123", airline = "DY",
                arrDep = "A", airport = "OSL",
                scheduleTime = now.plusHours(3).format(DateTimeFormatter.ISO_DATE_TIME)
            )

            mockAirportData("OSL", listOf(oslDep))
            mockAirportData("BGO", listOf(bgoArr))
            mockAirportsList(listOf("OSL", "BGO"))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertEquals(1, result.size)
            val flight = result.first()
            assertEquals("DY123", flight.flightId)
            assertEquals("OSL", flight.origin)
            assertEquals("BGO", flight.destination)
            assertEquals(2, flight.stops.size)
            assertNotNull(flight.stops.first().departureTime)
            assertNotNull(flight.stops.last().arrivalTime)
            Unit
        }

        @Test
        fun `should stitch multi-leg flight into 3 stops`() = runBlocking {
            val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)

            // TOS -> BOO -> SVJ
            val tosDep = createFlight(
                uniqueID = "1", flightId = "WF100", airline = "WF",
                arrDep = "D", airport = "SVJ", viaAirport = "BOO",
                scheduleTime = now.plusHours(1).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val booArr = createFlight(
                uniqueID = "2", flightId = "WF100", airline = "WF",
                arrDep = "A", airport = "TOS",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val booDep = createFlight(
                uniqueID = "3", flightId = "WF100", airline = "WF",
                arrDep = "D", airport = "SVJ",
                scheduleTime = now.plusHours(2).plusMinutes(15).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val svjArr = createFlight(
                uniqueID = "4", flightId = "WF100", airline = "WF",
                arrDep = "A", airport = "BOO",
                scheduleTime = now.plusHours(3).format(DateTimeFormatter.ISO_DATE_TIME)
            )

            mockAirportData("TOS", listOf(tosDep))
            mockAirportData("BOO", listOf(booArr, booDep))
            mockAirportData("SVJ", listOf(svjArr))
            mockAirportsList(listOf("TOS", "BOO", "SVJ"))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertEquals(1, result.size)
            val flight = result.first()
            assertEquals("WF100", flight.flightId)
            assertEquals(3, flight.stops.size)
            assertEquals("TOS", flight.origin)
            assertEquals("SVJ", flight.destination)
            assertTrue(flight.isMultiLeg)
        }

        @Test
        fun `should stitch circular flight with origin equal to destination`() = runBlocking {
            val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)

            // BOO -> RET -> LKN -> BOO (circular)
            val booDep = createFlight(
                uniqueID = "1", flightId = "WF892", airline = "WF",
                arrDep = "D", airport = "RET", viaAirport = "RET,LKN",
                scheduleTime = now.plusHours(1).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val retArr = createFlight(
                uniqueID = "2", flightId = "WF892", airline = "WF",
                arrDep = "A", airport = "BOO",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val retDep = createFlight(
                uniqueID = "3", flightId = "WF892", airline = "WF",
                arrDep = "D", airport = "LKN", viaAirport = "LKN",
                scheduleTime = now.plusHours(2).plusMinutes(15).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val lknArr = createFlight(
                uniqueID = "4", flightId = "WF892", airline = "WF",
                arrDep = "A", airport = "RET",
                scheduleTime = now.plusHours(3).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val lknDep = createFlight(
                uniqueID = "5", flightId = "WF892", airline = "WF",
                arrDep = "D", airport = "BOO",
                scheduleTime = now.plusHours(3).plusMinutes(15).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val booArr = createFlight(
                uniqueID = "6", flightId = "WF892", airline = "WF",
                arrDep = "A", airport = "LKN",
                scheduleTime = now.plusHours(4).format(DateTimeFormatter.ISO_DATE_TIME)
            )

            mockAirportData("BOO", listOf(booDep, booArr))
            mockAirportData("RET", listOf(retArr, retDep))
            mockAirportData("LKN", listOf(lknArr, lknDep))
            mockAirportsList(listOf("BOO", "RET", "LKN"))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertEquals(1, result.size)
            val flight = result.first()
            assertEquals("WF892", flight.flightId)
            assertEquals(4, flight.stops.size)
            assertEquals("BOO", flight.origin)
            assertEquals("BOO", flight.destination)
            assertNotNull(flight.stops.first().departureTime)
            assertNotNull(flight.stops.last().arrivalTime)
            Unit
        }

        @Test
        fun `should reject chain with gap in stops`() = runBlocking {
            val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)

            // OSL departs to BGO, but next stop seen is TRD — gap
            val oslDep = createFlight(
                uniqueID = "1", flightId = "DY999", airline = "DY",
                arrDep = "D", airport = "BGO",
                scheduleTime = now.plusHours(1).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val trdArr = createFlight(
                uniqueID = "2", flightId = "DY999", airline = "DY",
                arrDep = "A", airport = "OSL",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
            )

            mockAirportData("OSL", listOf(oslDep))
            mockAirportData("TRD", listOf(trdArr))
            mockAirportsList(listOf("OSL", "TRD"))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertFalse(result.any { it.flightId == "DY999" })
        }

        @Test
        fun `should infer missing arrival stop from departure target`() = runBlocking {
            val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)

            // Only departure from OSL to BGO, no arrival data from BGO
            val oslDep = createFlight(
                uniqueID = "1", flightId = "DY200", airline = "DY",
                arrDep = "D", airport = "BGO",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
            )

            mockAirportData("OSL", listOf(oslDep))
            mockAirportsList(listOf("OSL"))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertEquals(1, result.size)
            val flight = result.first()
            assertEquals(2, flight.stops.size)
            assertEquals("OSL", flight.origin)
            assertEquals("BGO", flight.destination)
        }

        @Test
        fun `should include Svalbard flights classified as international`() = runBlocking {
            val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)

            val oslDep = createFlight(
                uniqueID = "1", flightId = "DY660", airline = "DY",
                arrDep = "D", airport = "LYR",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "I"  // Avinor classifies LYR as international
            )

            mockAirportData("OSL", listOf(oslDep))
            mockAirportsList(listOf("OSL"))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertTrue(result.any { it.flightId == "DY660" })
        }
    }
}