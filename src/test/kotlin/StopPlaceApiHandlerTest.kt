import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gibil.routes.api.StopPlaceApiHandler
import org.gibil.service.ApiService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class StopPlaceApiHandlerTest {

    private lateinit var apiService: ApiService
    private lateinit var stopPlaceApiHandler: StopPlaceApiHandler

    @BeforeEach
    fun init(){
        apiService = mockk()
        stopPlaceApiHandler = StopPlaceApiHandler(apiService)
    }

    @Nested
    inner class StopPlaceApiUrlBuilder {

        @Test
        fun `stopPlaceApiUrlBuilder with all valid parameters returns url`() {
            val result = stopPlaceApiHandler.stopPlaceApiUrlBuilder(
                10,
                "AIR",
                "AIRPORT"
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