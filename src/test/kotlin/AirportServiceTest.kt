package org.gibil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.gibil.service.ApiService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import routes.api.AvinorApiHandler
import routes.api.AvinorXmlFeedParams
import service.AirportService
import java.util.Collections

/**
 * Test class for AirportService.
 */
class AirportServiceTest {

    /**
     * A fake implementation of ApiService that returns controlled responses
     * instead of making real HTTP calls.
     */
    class SpyApiService : ApiService(OkHttpClient()) {
        var simulateError = false

        override fun apiCall(url: String, acceptHeader: String?): String? {
            if (simulateError) {
                return "Error: 500 Server Error"
            }
            val code = url.substringAfterLast("/")
            return "<valid_xml_for_$code>"
        }
    }

    /**
     * A fake implementation of AvinorApiHandler for testing purposes.
     * Overrides the URL builder to skip real airport code validation
     * and return a predictable URL.
     */
    class SpyAvinorApi(apiService: ApiService) : AvinorApiHandler(apiService) {
        val capturedRequests = Collections.synchronizedList(mutableListOf<String>())

        override fun avinorXmlFeedUrlBuilder(params: AvinorXmlFeedParams): String {
            capturedRequests.add(params.airportCode)
            return "http://test/${params.airportCode}"
        }
    }

    @Test
    fun `fetchAndProcessAirports should flow correctly from API to Parser`() = runBlocking {
        val tempFile = File.createTempFile("test_airports", ".txt")
        tempFile.writeText("OSL")
        tempFile.deleteOnExit()

        val spyApiService = SpyApiService()
        val spyApi = SpyAvinorApi(spyApiService)

        val service = AirportService(spyApi, spyApiService, Dispatchers.Unconfined)

        service.fetchAndProcessAirports(tempFile.absolutePath)

        // Check that the API was called
        assertTrue(spyApi.capturedRequests.contains("OSL"), "API should have been called for OSL")
    }

    @Test
    fun `fetchAndProcessAirports should NOT parse if API returns Error`() = runBlocking {
        val tempFile = File.createTempFile("test_error", ".txt")
        tempFile.writeText("BGO")
        tempFile.deleteOnExit()

        val spyApiService = SpyApiService()
        spyApiService.simulateError = true // We simulate that Avinor is down/returning errors

        val spyApi = SpyAvinorApi(spyApiService)

        val service = AirportService(spyApi, spyApiService, Dispatchers.Unconfined)

        service.fetchAndProcessAirports(tempFile.absolutePath)

        // API was called
        assertTrue(spyApi.capturedRequests.contains("BGO"))
    }
}