package subscription

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import siri.SiriETPublisher
import java.util.concurrent.TimeUnit

private val LOG = LoggerFactory.getLogger(HttpHelper::class.java)

/**
 * Helper class for making HTTP POST requests, specifically for sending SIRI ET notifications.
 * It uses OkHttp3 HttpClient to perform HTTP operations.
 */
@Component
class HttpHelper() {

    val publisher = SiriETPublisher()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
    }

    /**
     * Sends a heartbeat notification to the specified address using the provided requestor reference.
     * The method constructs a SIRI heartbeat notification using the SiriETPublisher and sends it as an XML payload in a POST request.
     * @param address The URL to which the heartbeat notification should be sent.
     * @param requestorRef The reference identifier for the requester, used in the heartbeat notification.
     * @return The HTTP status code of the response, or -1 if the request fails
     */
    fun postHeartbeat(address: String, requestorRef: String): Int {
        val siri = SiriHelper.createHeartbeatNotification(requestorRef)
        return postData(address, publisher.toXml(siri))
    }

    /**
     * Sends a POST request to the specified URL with the given XML data as the body.
     * If verbose logging is enabled, it logs the XML data being sent. It also logs the response status code or any errors that occur during the request.
     * @param url The URL to which the POST request should be sent.
     * @param xmlData The XML data to be included in the body of the POST request. If null, no body will be sent.
     * @return The HTTP status code of the response, or -1 if the request fails
     */
    fun postData(url: String, xmlData: String?): Int {

        return try {
            val requestBuilder = Request.Builder()
                .url(url)

            if (xmlData != null) {
                val body = xmlData.toRequestBody(XML_MEDIA_TYPE)
                requestBuilder.post(body)
            } else {
                // If no XML data, don't send the request
                LOG.warn("No XML data provided, skipping POST to {}", url)
                return -1
            }

            val request = requestBuilder.build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error body"
                    LOG.error("POST request to {} failed with code {}. Error body: {}", url, response.code, errorBody)
                } else {
                    LOG.info("POST request to {} completed with response {}", url, response.code)
                }
                response.code
            }

        } catch (e: Exception) {
            LOG.error("POST request failed: {}", e.message, e)
            -1
        }
    }

    /**
     * Closes the HttpClient instance to release any resources it holds.
     * This should be called when the HttpHelper is no longer needed to ensure proper cleanup.
     */
    fun close() {
        httpClient.connectionPool.evictAll()
    }
}