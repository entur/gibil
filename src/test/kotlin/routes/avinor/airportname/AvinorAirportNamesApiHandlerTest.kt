package routes.avinor.airportname

import io.mockk.every
import io.mockk.mockk
import org.gibil.routes.avinor.airportname.AvinorAirportNamesApiHandler
import org.gibil.service.ApiService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [AvinorAirportNamesApiHandlerTest.TestConfig::class, AvinorAirportNamesApiHandler::class])
@TestPropertySource(locations = ["classpath:application.properties"])
class AvinorAirportNamesApiHandlerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        fun apiService(): ApiService = mockk {
            every { apiCall(any()) } returns """<airportNames><airportName code="OSL" name="Oslo Lufthavn"/><airportName code="BGO" name="Bergen Lufthavn"/></airportNames>"""
        }
    }

    @Autowired
    private lateinit var apiHandler: AvinorAirportNamesApiHandler

    @Test
    fun `AirportCodeValidator returns true on a valid airport code`() {
        Assertions.assertTrue(apiHandler.airportCodeValidator("OSL"))
    }

    @Test
    fun `AirportCodeValidator returns false on an invalid airport code`() {
        Assertions.assertFalse(apiHandler.airportCodeValidator("An invalid airportname"))
    }
}