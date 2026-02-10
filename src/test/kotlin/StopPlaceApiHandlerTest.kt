import io.mockk.mockk
import org.gibil.routes.api.StopPlaceApiHandler
import org.gibil.service.ApiService
import org.junit.jupiter.api.BeforeEach
import routes.api.AvinorApiHandler
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

    @Test
    fun `stopPlaceApiUrlBuilder with all valid parameters returns url`() {
       val result = stopPlaceApiHandler.stopPlaceApiUrlBuilder(
           10,
           "AIR",
           "AIRPORT"
       )
        val expectedUrl = "https://api.entur.io/stop-places/v1/read/stop-places?count=10&transportModes=AIR&stopPlaceTypes=AIRPORT"
        assertEquals(expectedUrl, result)
    }

    @Test
    fun `stopPlaceApiUrlBuilder with no parameters returns baseline url `() {
        val result = stopPlaceApiHandler.stopPlaceApiUrlBuilder()

        val expectedUrl = "https://api.entur.io/stop-places/v1/read/stop-places?count=100&transportModes=AIR&stopPlaceTypes=AIRPORT"
        assertEquals(expectedUrl, result)
    }
}