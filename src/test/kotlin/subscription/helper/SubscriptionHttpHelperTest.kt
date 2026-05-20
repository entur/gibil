package subscription.helper

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import util.XmlUtil

class SubscriptionHttpHelperTest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var SubscriptionHttpHelper: SubscriptionHttpHelper
    private lateinit var call: Call
    private lateinit var response: Response

    @BeforeEach
    fun setup() {
        httpClient = mockk()
        mockkObject(XmlUtil)
        SubscriptionHttpHelper = SubscriptionHttpHelper(httpClient)

        call = mockk()
        response = mockk(relaxed = true)

        every { httpClient.newCall(any()) } returns call
        every { call.execute() } returns response
    }

    @AfterEach
    fun teardown() {
        unmockkObject(XmlUtil)
    }

    @Nested
    inner class PostHeartbeat {
        @Test
        fun `postHeartbeat returns response code from server`() {
            every { response.isSuccessful } returns true
            every { response.code } returns 200
            every { XmlUtil.toXml(any(), any()) } returns "<heartbeat/>"

            val result = SubscriptionHttpHelper.postHeartbeat("http://localhost/heartbeat", "ENTUR_DEV")

            Assertions.assertEquals(200, result)
            verify(exactly = 1) { XmlUtil.toXml(any(), any()) }
            verify(exactly = 1) { httpClient.newCall(any()) }
        }
    }

    @Nested
    inner class PostData {

        @Test
        fun `postData returns response code on success`() {
            every { response.isSuccessful } returns true
            every { response.code } returns 200

            val result = SubscriptionHttpHelper.postData("http://localhost/test", "<xml>data</xml>")

            Assertions.assertEquals(200, result)
        }

        @Test
        fun `postData returns error code on non-successful response`() {
            every { response.isSuccessful } returns false
            every { response.code } returns 500
            every { response.body } returns null

            val result = SubscriptionHttpHelper.postData("http://localhost/test", "<xml>data</xml>")

            Assertions.assertEquals(500, result)
        }

        @Test
        fun `postData returns -1 when xmlData is null`() {
            val result = SubscriptionHttpHelper.postData("http://localhost/test", null)

            Assertions.assertEquals(-1, result)
            verify(exactly = 0) { httpClient.newCall(any()) }
        }

        @Test
        fun `postData returns -1 when HTTP call throws exception`() {
            every { call.execute() } throws RuntimeException("Connection refused")

            val result = SubscriptionHttpHelper.postData("http://localhost/test", "<xml>data</xml>")

            Assertions.assertEquals(-1, result)
        }
    }
}