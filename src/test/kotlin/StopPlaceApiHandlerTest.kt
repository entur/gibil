import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gibil.routes.api.StopPlaceApiHandler
import org.gibil.service.ApiService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [StopPlaceApiHandlerTest.TestConfig::class, StopPlaceApiHandler::class])
@TestPropertySource(locations = ["classpath:application.properties"])
class StopPlaceApiHandlerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun apiService(): ApiService = mockk()
    }

    @Autowired
    private lateinit var stopPlaceApiHandler: StopPlaceApiHandler

    @Autowired
    private lateinit var apiService: ApiService

    @Nested
    inner class StopPlaceApiUrlBuilder {

        @Test
        fun `stopPlaceApiUrlBuilder with all valid parameters returns url`() {
            val result = stopPlaceApiHandler.stopPlaceApiUrlBuilder(
                10
            )
            val expectedUrl =
                "https://api.entur.io/stop-places/v1/read/stop-places?count=10&transportModes=AIR&stopPlaceTypes=AIRPORT"
            assertEquals(expectedUrl, result)
        }

        @Test
        fun `stopPlaceApiUrlBuilder with no parameters returns baseline url `() {
            val result = stopPlaceApiHandler.stopPlaceApiUrlBuilder()

            val expectedUrl =
                "https://api.entur.io/stop-places/v1/read/stop-places?count=100&transportModes=AIR&stopPlaceTypes=AIRPORT"
            assertEquals(expectedUrl, result)
        }

        @Test
        fun `stopPlaceApiUrlBuilder throws IllegalArgument on zero count`() {
            assertThrows<IllegalArgumentException> {
                stopPlaceApiHandler.stopPlaceApiUrlBuilder(stopPlaceCount = 0)
            }
        }

        @Test
        fun `stopPlaceApiUrlBuilder throws IllegalArgument on negative count`() {
            assertThrows<IllegalArgumentException> {
                stopPlaceApiHandler.stopPlaceApiUrlBuilder(stopPlaceCount = -10)
            }
        }
    }

    @Nested
    inner class FetchAirportStopPlaces {

        @Test
        fun `fetchAirportStopPlaces should call apiService with correct url and content type`() {
            val expectedUrl =
                "https://api.entur.io/stop-places/v1/read/stop-places?count=100&transportModes=AIR&stopPlaceTypes=AIRPORT"
            val expectedResponse =
                "<xml>stopPlaces</xml>"
            every { apiService.apiCall(expectedUrl, "application/xml") } returns expectedResponse

            stopPlaceApiHandler.fetchAirportStopPlaces()

            verify { apiService.apiCall(expectedUrl, "application/xml") }
        }

        @Test
        fun `fetchAiportStopPlaces should return valid XML response`() {
            val expectedResponse = "<xml><stopPlaces><stopPlace>Oslo Lufthavn</stopPlace></stopPlaces></xml></xml>"
            every { apiService.apiCall(any(), "application/xml") } returns expectedResponse

            val response = stopPlaceApiHandler.fetchAirportStopPlaces()

            assertEquals(expectedResponse, response)
        }
    }
}
