package service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gibil.StopPlaceMapper
import org.gibil.model.stopPlacesApi.StopPlaces
import org.gibil.routes.api.StopPlaceApiHandler
import org.gibil.service.AirportQuayService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import kotlin.test.assertEquals

class AirportQuayServiceTest {

    private lateinit var handler: StopPlaceApiHandler
    private lateinit var mapper: StopPlaceMapper
    private lateinit var airportQuayService: AirportQuayService

    @BeforeEach
    fun init() {
        handler = mockk()
        mapper = mockk()

        airportQuayService = AirportQuayService(handler, mapper)
    }

    @Nested
    inner class RefreshQuayMapping {

       @Test
        fun `refreshQuayMapping populates map when API returns valid XML`() {

            val validXml = "<xml>stopPlaces</xml>"
            val stopPlaces = StopPlaces()
            val expectedMap = mapOf(
                "OSL" to listOf("NSR:Quay:1173"),
                "BGO" to listOf("NSR:Quay:1213")
            )
            every { handler.fetchAirportStopPlaces() } returns validXml
            every { mapper.unmarshallStopPlaceXml(validXml)} returns stopPlaces
            every { mapper.makeIataToQuayMap(stopPlaces) } returns expectedMap

            airportQuayService.refreshQuayMapping()

            assertEquals("NSR:Quay:1173", airportQuayService.getQuayId("OSL"))
            assertEquals("NSR:Quay:1213", airportQuayService.getQuayId("BGO"))
        }

        @Test
        fun `refreshQuayMapping does not populates map when API returns null`() {
            every { handler.fetchAirportStopPlaces() } returns null

            airportQuayService.refreshQuayMapping()
            verify(exactly = 0) { mapper.unmarshallStopPlaceXml(any()) }
            verify(exactly = 0) { mapper.makeIataToQuayMap(any()) }
        }

        @Test
        fun `refreshQuayMapping should replace old quay on refresh`() {
            val initialValidXml = "<xml>stopPlaces</xml>"
            val initialStopPlaces = StopPlaces()
            val initialExpectedMap = mapOf(
                "OSL" to listOf("NSR:Quay:1173"),
                "BGO" to listOf("NSR:Quay:1213")
            )
            every { handler.fetchAirportStopPlaces() } returns initialValidXml
            every { mapper.unmarshallStopPlaceXml(initialValidXml)} returns initialStopPlaces
            every { mapper.makeIataToQuayMap(initialStopPlaces) } returns initialExpectedMap

            airportQuayService.refreshQuayMapping()

            assertEquals("NSR:Quay:1173", airportQuayService.getQuayId("OSL"))
            assertEquals("NSR:Quay:1213", airportQuayService.getQuayId("BGO"))

            val newValidXml = "<xml>stopPlaces</xml>"
            val newStopPlaces = StopPlaces()
            val newExpectedMap = mapOf(
                "OSL" to listOf("NSR:Quay:9373"),
                "BGO" to listOf("NSR:Quay:9713")
            )
            every { handler.fetchAirportStopPlaces() } returns newValidXml
            every { mapper.unmarshallStopPlaceXml(newValidXml)} returns newStopPlaces
            every { mapper.makeIataToQuayMap(newStopPlaces) } returns newExpectedMap

            airportQuayService.refreshQuayMapping()

            assertEquals("NSR:Quay:9373", airportQuayService.getQuayId("OSL"))
            assertEquals("NSR:Quay:9713", airportQuayService.getQuayId("BGO"))
        }
    }

    @Nested
    inner class GetQuayId {

        @BeforeEach
        fun setUp() {
            val validXml = "<xml>stopPlaces</xml>"
            val stopPlaces = StopPlaces()
            val expectedMap = mapOf(
                "OSL" to listOf("NSR:Quay:1173"),
                "BGO" to listOf("NSR:Quay:1213")
            )
            every { handler.fetchAirportStopPlaces() } returns validXml
            every { mapper.unmarshallStopPlaceXml(validXml)} returns stopPlaces
            every { mapper.makeIataToQuayMap(stopPlaces) } returns expectedMap

            airportQuayService.refreshQuayMapping()
        }

        @Test
        fun `getQuayId returns expected value`() {
            val resultOSL = airportQuayService.getQuayId("OSL")
            val resultBGO = airportQuayService.getQuayId("BGO")

            assertEquals("NSR:Quay:1173", resultOSL)
            assertEquals("NSR:Quay:1213", resultBGO)
        }

        @Test
        fun `getQuayId returns null when airport does not have a quay`() {
            val result = airportQuayService.getQuayId("KRS")

            assertNull(result)
        }


    }

}