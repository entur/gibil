package routes.avinor.airportname

import io.mockk.every
import io.mockk.mockk
import org.gibil.routes.avinor.airportname.AvinorAirportNamesApiHandler
import org.gibil.service.ApiService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AvinorAirportNamesApiHandlerTest {

    private lateinit var apiService: ApiService
    private lateinit var apiHandler: AvinorAirportNamesApiHandler

    @BeforeEach
    fun setUp() {
        apiService = mockk {
            every { apiCall(any()) } returns """<airportNames><airportName code="OSL" name="Oslo Lufthavn"/><airportName code="BGO" name="Bergen Lufthavn"/></airportNames>"""
        }
        apiHandler = AvinorAirportNamesApiHandler(apiService, "http://example.com")
        apiHandler.init()
    }

    @Test
    fun `AirportCodeValidator returns true on a valid airport code`() {
        Assertions.assertTrue(apiHandler.airportCodeValidator("OSL"))
    }

    @Test
    fun `AirportCodeValidator returns false on an invalid airport code`() {
        Assertions.assertFalse(apiHandler.airportCodeValidator("An invalid airportname"))
    }
}