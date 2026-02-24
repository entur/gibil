package service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Response
import okhttp3.Request
import okhttp3.ResponseBody
import org.gibil.service.ApiService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals

class ApiServiceTest {

    private val mockClient = mockk<OkHttpClient>()
    private val mockCall = mockk<Call>()
    private val mockResponse = mockk<Response>()
    private val mockBody = mockk<ResponseBody>()

    private val apiService = ApiService(mockClient)

    @Test
    fun `apiCall returns body string on success`() {
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns mockBody
        every { mockBody.string() } returns "<xml>data</xml>"
        every { mockResponse.close() } returns Unit  // needed for response.use {}

        val result = apiService.apiCall("https://example.com")

        assertEquals("<xml>data</xml>", result)
    }

    @Test
    fun `apiCall throws IOException on failure`() {
        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.code } returns 404
        every { mockResponse.close() } returns Unit

        Assertions.assertThrows(IOException::class.java) {
            apiService.apiCall("https://example.com")
        }
    }

    @Test
    fun `apiCall acceptheader added correctly when provided`() {
        //make sure to capture request sent
        val requestSlot = slot<Request>()
        every { mockClient.newCall(capture(requestSlot)) } returns mockCall

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns mockBody
        every { mockBody.string() } returns "<xml>data</xml>"
        every { mockResponse.close() } returns Unit

        apiService.apiCall("https://example.com", acceptHeader = "application/xml")

        assertEquals("application/xml", requestSlot.captured.header("Accept"))
    }

    @Test
    fun `apiCallToFile writes response body to file`() {
        val targetFile = File.createTempFile("test", ".xml")
        val fileContent = "<xml>data</xml>"
        val inputStream = fileContent.byteInputStream()

        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns mockBody
        every { mockBody.byteStream() } returns inputStream
        every { mockResponse.close() } returns Unit

        apiService.apiCallToFile("https://example.com", targetFile)

        assertEquals(fileContent, targetFile.readText())
        targetFile.deleteOnExit()
    }

    @Test
    fun `apiCallToFile acceptheader added correctly when provided`() {
        //make sure to capture request sent
        val requestSlot = slot<Request>()
        every { mockClient.newCall(capture(requestSlot)) } returns mockCall

        val targetFile = File.createTempFile("test", ".xml")
        val fileContent = "<xml>data</xml>"
        val inputStream = fileContent.byteInputStream()

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body } returns mockBody
        every { mockBody.byteStream() } returns inputStream
        every { mockResponse.close() } returns Unit

        apiService.apiCallToFile("https://example.com", targetFile, acceptHeader = "application/xml")

        assertEquals("application/xml", requestSlot.captured.header("Accept"))

        targetFile.deleteOnExit()
    }

    @Test
    fun `apiCallToFile throws IOException when request fails`() {
        val targetFile = File.createTempFile("test", ".xml")
        val fileContent = "<xml>data</xml>"
        val inputStream = fileContent.byteInputStream()

        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.close() } returns Unit

        apiService.apiCallToFile("https://example.com", targetFile)

        assertEquals(fileContent, targetFile.readText())
        targetFile.deleteOnExit()
    }
}