package routes.avinor.xmlfeed

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedParamsLogic
import org.gibil.routes.avinor.xmlfeed.AvinorXmlFeedApiHandler
import org.gibil.routes.avinor.airportname.AvinorAirportNamesApiHandler
import org.gibil.service.ApiService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI

class AvinorXmlFeedApiHandlerTest {

    private lateinit var airportNamesHandler: AvinorAirportNamesApiHandler
    private lateinit var apiService: ApiService
    private lateinit var apiHandler: AvinorXmlFeedApiHandler

    @BeforeEach
    fun setUp() {
        airportNamesHandler = mockk {
            every { airportCodeValidator("OSL") } returns true
            every { airportCodeValidator("BGO") } returns true
            every { airportCodeValidator(not(or(eq("OSL"), eq("BGO")))) } returns false
        }
        apiService = mockk()
        apiHandler = AvinorXmlFeedApiHandler(airportNamesHandler, apiService, "http://fake-url")
    }

    @Test
    fun `avinorXmlFeedUrlBuilder constructs correct URL with all parameters`() {
        // Arrange
        val params = AvinorXmlFeedParamsLogic(
            airportCode = "OSL",
            timeFrom = 1,
            timeTo = 7,
            direction = "D"
        )

        // Act
        val resultUrl = apiHandler.avinorXmlFeedUrlBuilder(params)

        // Assert
        Assertions.assertNotNull(resultUrl)

        // Verify it is a valid URI
        Assertions.assertDoesNotThrow { URI.create(resultUrl) }

        // Verify query parameters exist (order might vary, so checking containment is safer)
        Assertions.assertTrue(resultUrl.contains("airport=OSL"), "URL should contain airport param")
        Assertions.assertTrue(resultUrl.contains("TimeFrom=1"), "URL should contain TimeFrom param")
        Assertions.assertTrue(resultUrl.contains("TimeTo=7"), "URL should contain TimeTo param")
        Assertions.assertTrue(resultUrl.contains("direction=D"), "URL should contain direction param")
    }

    @Test
    fun `avinorXmlFeedUrlBuilder excludes direction when not provided`() {
        // Arrange (Assuming params allows null/empty direction or you pass a neutral one)
        // If your new Model enforces direction logic, use a case where direction is ignored or optional.
        // Based on your code, it only adds param if direction is "A" or "D".
        // Let's pass a param that SHOULD be ignored if logic allows, or create a specific case.

        val params = AvinorXmlFeedParamsLogic(
            airportCode = "OSL",
            timeFrom = 1,
            timeTo = 7,
            direction = null
        )

        // Act
        val resultUrl = apiHandler.avinorXmlFeedUrlBuilder(params)

        // Assert
        Assertions.assertFalse(
            resultUrl.contains("direction="),
            "URL should NOT contain direction param for empty input"
        )
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with all valid parameters returns URL`() {
        val result = apiHandler.avinorXmlFeedUrlBuilder(
            AvinorXmlFeedParamsLogic(
                airportCode = "OSL",
                timeFrom = 1,
                timeTo = 7,
                direction = "D"
            )
        )

        requireNotNull(result) { "url builder returned null" }
        Assertions.assertTrue(result.contains("airport=OSL"))
        Assertions.assertTrue(result.contains("direction"))
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with invalid airport code throws exception`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            apiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParamsLogic(
                    airportCode = "OS",
                    timeFrom = 1,
                    timeTo = 7,
                    direction = "D",
                )
            )
        }
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with negative time from throws exception`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            apiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParamsLogic(
                    airportCode = "OSL",
                    timeFrom = -100,
                    timeTo = 7,
                    direction = "D",
                )
            )
        }
    }

    @Test
    fun `avinorXmlFeedUrlBuilder with time exceeding limit throws exception`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            apiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParamsLogic(
                    airportCode = "OSL",
                    timeFrom = 1,
                    timeTo = 700000000,
                    direction = "D",
                )
            )
        }
    }

    @Test
    fun `avinorXmlFeedUrlBuilder missing airportCode should throw`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            apiHandler.avinorXmlFeedUrlBuilder(
                AvinorXmlFeedParamsLogic(
                    airportCode = "",
                    timeFrom = 1,
                    timeTo = 7,
                    direction = "D",
                )
            )
        }
    }

    @Test
    fun `fetchFlights returns successful result when API call succeeds`() {
        val params = AvinorXmlFeedParamsLogic(airportCode = "OSL")
        every { apiService.apiCall(any(), any()) } returns Result.success("<xml>flights</xml>")

        val result = apiHandler.fetchFlights(params)

        Assertions.assertTrue(result.isSuccess)
        Assertions.assertEquals("<xml>flights</xml>", result.getOrNull())
        verify { apiService.apiCall(match { it.contains("airport=OSL") }, any()) }
    }

    @Test
    fun `fetchFlights returns failure result when API call fails`() {
        val params = AvinorXmlFeedParamsLogic(airportCode = "OSL")
        every { apiService.apiCall(any(), any()) } returns Result.failure(IOException("HTTP code: 500"))

        val result = apiHandler.fetchFlights(params)

        Assertions.assertTrue(result.isFailure)
    }

    @Test
    fun `fetchFlights throws when airport code is invalid`() {
        val params = AvinorXmlFeedParamsLogic(airportCode = "XXX")

        Assertions.assertThrows(IllegalArgumentException::class.java) {
            apiHandler.fetchFlights(params)
        }
    }
}