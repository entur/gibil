package routes.api

import io.mockk.every
import io.mockk.mockk
import org.gibil.service.ApiService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import routes.api.AvinorApiHandler
import model.AvinorXmlFeedParams
import java.net.URI

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [AvinorApiHandlerTest.TestConfig::class, AvinorApiHandler::class])
@TestPropertySource(locations = ["classpath:application.properties"])
class AvinorApiHandlerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun apiService(): ApiService = mockk {
            every { apiCall(any()) } returns """<airportNames><airportName code="OSL" name="Oslo Lufthavn"/><airportName code="BGO" name="Bergen Lufthavn"/></airportNames>"""
        }
    }

    @Autowired
    private lateinit var apiHandler: AvinorApiHandler

    @Test
    fun `avinorXmlFeedUrlBuilder constructs correct URL with all parameters`() {
        // Arrange
        val params = AvinorXmlFeedParams(
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

        val params = AvinorXmlFeedParams(
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
            AvinorXmlFeedParams(
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
                AvinorXmlFeedParams(
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
                AvinorXmlFeedParams(
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
                AvinorXmlFeedParams(
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
                AvinorXmlFeedParams(
                    airportCode = "",
                    timeFrom = 1,
                    timeTo = 7,
                    direction = "D",
                )
            )
        }
    }
}
