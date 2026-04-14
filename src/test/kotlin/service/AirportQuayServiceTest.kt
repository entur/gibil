package service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gibil.handler.StopPlaceMapper
import org.gibil.model.stopPlaces.StopPlaces
import org.gibil.service.ApiService
import org.gibil.service.AirportQuayService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals

class AirportQuayServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mapper: StopPlaceMapper
    private lateinit var apiService: ApiService
    private lateinit var airportQuayService: AirportQuayService

    @BeforeEach
    fun init() {
        mapper = mockk()
        apiService = mockk()
        airportQuayService = AirportQuayService(mapper, apiService, "https://dummy-url", tempDir.toString())
    }

    @Nested
    inner class RefreshQuayMapping {

        @Test
        fun `refreshQuayMapping populates map when file is parsed successfully`() {
            tempDir.resolve("test.xml").toFile().createNewFile()
            val stopPlaces = StopPlaces()
            val expectedMap = mapOf(
                "OSL" to listOf("NSR:Quay:1173"),
                "BGO" to listOf("NSR:Quay:1213")
            )
            every { mapper.parseStopPlaceFromFile(any()) } returns stopPlaces
            every { mapper.makeIataToQuayMap(stopPlaces) } returns expectedMap

            airportQuayService.refreshQuayMapping()

            assertEquals("NSR:Quay:1173", airportQuayService.getQuayId("OSL"))
            assertEquals("NSR:Quay:1213", airportQuayService.getQuayId("BGO"))
        }

        @Test
        fun `refreshQuayMapping does not update map when parsing fails`() {
            tempDir.resolve("test.xml").toFile().createNewFile()
            every { mapper.parseStopPlaceFromFile(any()) } throws RuntimeException("Parse error")

            airportQuayService.refreshQuayMapping()

            verify(exactly = 0) { mapper.makeIataToQuayMap(any()) }
        }

        @Test
        fun `refreshQuayMapping does not update map when no XML file found`() {
            airportQuayService.refreshQuayMapping()

            verify(exactly = 0) { mapper.parseStopPlaceFromFile(any()) }
            verify(exactly = 0) { mapper.makeIataToQuayMap(any()) }
        }

        @Test
        fun `refreshQuayMapping should replace old quay data on refresh`() {
            tempDir.resolve("test.xml").toFile().createNewFile()
            val initialMap = mapOf("OSL" to listOf("NSR:Quay:1173"))
            val newMap = mapOf("OSL" to listOf("NSR:Quay:9373"))

            every { mapper.parseStopPlaceFromFile(any()) } returns StopPlaces()
            every { mapper.makeIataToQuayMap(any()) } returnsMany listOf(initialMap, newMap)

            airportQuayService.refreshQuayMapping()
            assertEquals("NSR:Quay:1173", airportQuayService.getQuayId("OSL"))

            airportQuayService.refreshQuayMapping()
            assertEquals("NSR:Quay:9373", airportQuayService.getQuayId("OSL"))
        }
    }

    @Nested
    inner class GetQuayId {

        @BeforeEach
        fun setUp() {
            tempDir.resolve("test.xml").toFile().createNewFile()
            val expectedMap = mapOf(
                "OSL" to listOf("NSR:Quay:1173"),
                "BGO" to listOf("NSR:Quay:1213")
            )
            every { mapper.parseStopPlaceFromFile(any()) } returns StopPlaces()
            every { mapper.makeIataToQuayMap(any()) } returns expectedMap

            airportQuayService.refreshQuayMapping()
        }

        @Test
        fun `getQuayId returns expected value`() {
            assertEquals("NSR:Quay:1173", airportQuayService.getQuayId("OSL"))
            assertEquals("NSR:Quay:1213", airportQuayService.getQuayId("BGO"))
        }

        @Test
        fun `getQuayId returns null when airport does not have a quay`() {
            assertNull(airportQuayService.getQuayId("KRS"))
        }
    }
}
