package service

import handler.AvinorScheduleXmlHandler
import io.mockk.*
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import model.xmlFeedApi.*
import org.gibil.util.Dates
import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedApiHandler
import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedParamsLogic
import org.junit.jupiter.api.*

class FlightAggregationServiceTest {

    private lateinit var avinorXmlFeedApiHandler: AvinorXmlFeedApiHandler
    private lateinit var xmlHandler: AvinorScheduleXmlHandler
    private lateinit var flightAggregationService: FlightAggregationService
    private lateinit var ioDispatcher: CoroutineDispatcher

    @BeforeEach
    fun init() {
        avinorXmlFeedApiHandler = mockk {
            every { fetchFlights(any()) } returns Result.failure(RuntimeException("unmocked airport"))
        }
        xmlHandler = mockk()
        ioDispatcher = Dispatchers.Unconfined

        flightAggregationService = FlightAggregationService(avinorXmlFeedApiHandler, xmlHandler, ioDispatcher)
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
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

        every {
            avinorXmlFeedApiHandler.fetchFlights(
                match<AvinorXmlFeedParamsLogic> { it.airportCode == airportCode }
            )
        } returns Result.success("<xml>$airportCode</xml>")
        every { xmlHandler.unmarshallXmlToAirport("<xml>$airportCode</xml>") } returns airport
    }

    @Nested
    inner class FetchUnifiedFlights {

        @Test
        fun `should stitch direct flight into 2 stops`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

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
            val now = ZonedDateTime.now(ZoneOffset.UTC)

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
            val now = ZonedDateTime.now(ZoneOffset.UTC)

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
            val now = ZonedDateTime.now(ZoneOffset.UTC)

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

            val result = flightAggregationService.fetchUnifiedFlights()

            assertFalse(result.any { it.flightId == "DY999" })
        }

        @Test
        fun `should exclude flight when only departure is observed`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            val oslDep = createFlight(
                uniqueID = "1", flightId = "DY200", airline = "DY",
                arrDep = "D", airport = "BGO",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
            )

            mockAirportData("OSL", listOf(oslDep))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertFalse(result.any { it.flightId == "DY200" })
        }

        @Test
        fun `should exclude flight when only arrival is observed`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            val bgoArr = createFlight(
                uniqueID = "1", flightId = "DY300", airline = "DY",
                arrDep = "A", airport = "OSL",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
            )

            mockAirportData("BGO", listOf(bgoArr))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertFalse(result.any { it.flightId == "DY300" })
        }

        @Test
        fun `should filter out international flights`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            val domesticFlight = createFlight(
                uniqueID = "1", flightId = "DY123",
                arrDep = "D", airport = "BGO",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "D"
            )
            val domesticArrival = createFlight(
                uniqueID = "3", flightId = "DY123",
                arrDep = "A", airport = "OSL",
                scheduleTime = now.plusHours(3).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "D"
            )
            val internationalFlight = createFlight(
                uniqueID = "2", flightId = "DY456",
                arrDep = "D", airport = "CPH",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "I"
            )

            mockAirportData("OSL", listOf(domesticFlight, internationalFlight))
            mockAirportData("BGO", listOf(domesticArrival))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertTrue(result.any { it.flightId == "DY123" })
            assertFalse(result.any { it.flightId == "DY456" })
        }

        @Test
        fun `should include Svalbard flights classified as international`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)

            val oslDep = createFlight(
                uniqueID = "1", flightId = "DY660", airline = "DY",
                arrDep = "D", airport = "LYR",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "I"  // Avinor classifies LYR as international
            )
            val lyrArr = createFlight(
                uniqueID = "2", flightId = "DY660", airline = "DY",
                arrDep = "A", airport = "OSL",
                scheduleTime = now.plusHours(3).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "I"
            )

            mockAirportData("OSL", listOf(oslDep))
            mockAirportData("LYR", listOf(lyrArr))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertTrue(result.any { it.flightId == "DY660" })
        }

        @Test
        fun `should drop chains outside allowed window (too old or too far ahead)`() = runBlocking {
            val now = ZonedDateTime.parse("2026-01-01T12:00:00Z")
            mockkObject(Dates)
            every { Dates.instantNowUtc() } returns now

            val tooOldDep = createFlight(
                uniqueID = "1", flightId = "DY111",
                arrDep = "D", airport = "BGO",
                scheduleTime = now.minusMinutes(30).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val tooOldArr = createFlight(
                uniqueID = "2", flightId = "DY111",
                arrDep = "A", airport = "OSL",
                scheduleTime = now.minusMinutes(21).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            // Remove status timestamps so the logic falls back to scheduled stop times.
            tooOldDep.status = null
            tooOldArr.status = null

            val tooFarDep = createFlight(
                uniqueID = "3", flightId = "DY222",
                arrDep = "D", airport = "BGO",
                scheduleTime = now.plusHours(25).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val tooFarArr = createFlight(
                uniqueID = "4", flightId = "DY222",
                arrDep = "A", airport = "OSL",
                scheduleTime = now.plusHours(26).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            tooFarDep.status = null
            tooFarArr.status = null

            mockAirportData("OSL", listOf(tooOldDep, tooFarDep))
            mockAirportData("BGO", listOf(tooOldArr, tooFarArr))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertFalse(result.any { it.flightId == "DY111" })
            assertFalse(result.any { it.flightId == "DY222" })
        }

        @Test
        fun `should drop chain when latest status time is outside allowed past window`() = runBlocking {
            val now = ZonedDateTime.parse("2026-01-01T12:00:00Z")
            mockkObject(Dates)
            every { Dates.instantNowUtc() } returns now

            val dep = createFlight(
                uniqueID = "1", flightId = "DY333",
                arrDep = "D", airport = "BGO",
                scheduleTime = now.plusMinutes(10).format(DateTimeFormatter.ISO_DATE_TIME)
            )
            val arr = createFlight(
                uniqueID = "2", flightId = "DY333",
                arrDep = "A", airport = "OSL",
                scheduleTime = now.plusMinutes(40).format(DateTimeFormatter.ISO_DATE_TIME)
            )

            val staleStatus = now.minusMinutes(FlightAggregationService.MAX_PAST_MINUTES + 2)
                .format(DateTimeFormatter.ISO_DATE_TIME)
            // Status times represent the latest real-world event and are used for the "too old" check.
            dep.status = FlightStatus().apply {
                code = "D"
                time = staleStatus
            }
            arr.status = FlightStatus().apply {
                code = "A"
                time = staleStatus
            }

            mockAirportData("OSL", listOf(dep))
            mockAirportData("BGO", listOf(arr))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertFalse(result.any { it.flightId == "DY333" })
        }

        @Test
        fun `should handle API errors gracefully`() = runBlocking {
            every { avinorXmlFeedApiHandler.fetchFlights(any()) } returns Result.failure(IOException("API unavailable"))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertEquals(0, result.size)
        }

        @Test
        fun `should handle exception thrown during airport data fetch`() = runBlocking {
            every { avinorXmlFeedApiHandler.fetchFlights(any()) } throws RuntimeException("Connection timeout")

            val result = flightAggregationService.fetchUnifiedFlights()

            assertEquals(0, result.size)
        }

        @Test
        fun `should filter out flight with null flight id`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            val nullIdFlight = Flight().apply {
                this.flightId = null
                this.arrDep = "D"
                this.airport = "BGO"
                this.scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
                this.domInt = "D"
                this.status = FlightStatus().apply { this.code = "N" }
            }

            mockAirportData("OSL", listOf(nullIdFlight))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertEquals(0, result.size)
        }

        @Test
        fun `should filter out flight with single character flight id`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            val shortIdFlight = createFlight(
                uniqueID = "1", flightId = "D",
                arrDep = "D", airport = "BGO",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME)
            )

            mockAirportData("OSL", listOf(shortIdFlight))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertEquals(0, result.size)
        }

        @Test
        fun `should include flights fetched from Svalbard airport`() = runBlocking {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            val lyrDep = createFlight(
                uniqueID = "1", flightId = "DY661", airline = "DY",
                arrDep = "D", airport = "OSL",
                scheduleTime = now.plusHours(2).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "I"
            )
            val oslArr = createFlight(
                uniqueID = "2", flightId = "DY661", airline = "DY",
                arrDep = "A", airport = "LYR",
                scheduleTime = now.plusHours(3).format(DateTimeFormatter.ISO_DATE_TIME),
                domInt = "I"
            )

            mockAirportData("LYR", listOf(lyrDep))
            mockAirportData("OSL", listOf(oslArr))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertTrue(result.any { it.flightId == "DY661" })
        }

        @Test
        fun `should skip flights with null schedule time`() = runBlocking {
            val nullTimeFlight = createFlight(
                uniqueID = "1", flightId = "DY400",
                arrDep = "D", airport = "BGO",
                scheduleTime = null
            )

            mockAirportData("OSL", listOf(nullTimeFlight))

            val result = flightAggregationService.fetchUnifiedFlights()

            assertFalse(result.any { it.flightId == "DY400" })
        }
    }
}