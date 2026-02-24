import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jakarta.xml.bind.Unmarshaller
import org.gibil.StopPlaceMapper
import org.gibil.model.stopPlacesApi.StopPlaces
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import util.SharedJaxbContext
import java.io.StringReader

class StopPlaceMapperTest {

    private lateinit var stopPlaceMapper: StopPlaceMapper

    @BeforeEach
    fun init() {
        stopPlaceMapper = StopPlaceMapper()
    }

    @Nested
    inner class UnmarshallStopPlaceXml {

        @BeforeEach
        fun setUp() {
            mockkObject(SharedJaxbContext)
        }

        @AfterEach
        fun tearDown() {
            unmockkObject(SharedJaxbContext)
        }

        @Test
        fun `unmarshallStopPlaceXml returns StopPlaces when airport `() {
            val validXml = "<xml>stopPlaces</xml>"
            val expectedStopPlaces = mockk<StopPlaces>()
            val unmarshaller = mockk<Unmarshaller>()

            every { SharedJaxbContext.createUnmarshaller() } returns unmarshaller
            every { unmarshaller.unmarshal(any<StringReader>()) } returns expectedStopPlaces

            val result = stopPlaceMapper.unmarshallStopPlaceXml(validXml)
            assertEquals(expectedStopPlaces, result)
        }

        @Test
        fun `unmarshallStopPlaceXml throws exception when xml is invalid`() {
            val invalidXml = "<xml>stopPlaces<"
            val unmarshaller = mockk<Unmarshaller>()

            every { SharedJaxbContext.createUnmarshaller() } returns unmarshaller
            every { unmarshaller.unmarshal(any<StringReader>()) } throws Exception("Parse error")

            val exception = assertThrows<RuntimeException> {
                stopPlaceMapper.unmarshallStopPlaceXml(invalidXml)
            }

            assertEquals("Error parsing StopPlaces", exception.message)
            assertTrue(exception.cause is Exception)
        }
    }

    //TODO MORE TESTS ON THE QUAYMAP
}