package subscription

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import siri.SiriETPublisher

/**
 * Helper class for making HTTP POST requests, specifically for sending SIRI ET notifications.
 * It uses Ktor's HttpClient with the CIO engine to perform HTTP operations.
 */
@Component
class HttpHelper(
    private val verbose: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(HttpHelper::class.java)
    val publisher = SiriETPublisher()

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONN_TIMEOUT
            requestTimeoutMillis = CONN_TIMEOUT
            socketTimeoutMillis = SOCKET_TIMEOUT
        }
    }

    /**
     * Sends a heartbeat notification to the specified address using the provided requestor reference.
     * The method constructs a SIRI heartbeat notification using the SiriETPublisher and sends it as an XML payload in a POST request.
     * @param address The URL to which the heartbeat notification should be sent.
     * @param requestorRef The reference identifier for the requester, used in the heartbeat notification.
     * @return The HTTP status code of the response, or -1 if the request fails
     */
    suspend fun postHeartbeat(address: String, requestorRef: String): Int {
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
    suspend fun postData(url: String, xmlData: String?): Int {
        if (verbose && xmlData != null) {
            logger.info(xmlData)
        }

        return try {
            val response = httpClient.post(url) {
                if (xmlData != null) {
                    contentType(ContentType.Application.Xml)
                    setBody(xmlData)
                }
            }
            logger.info("POST request completed with response {}", response.status.value)
            response.status.value
        } catch (e: Exception) {
            logger.error("POST request failed: ${e.message}")
            -1
        }
    }

    /**
     * Closes the HttpClient instance to release any resources it holds.
     * This should be called when the HttpHelper is no longer needed to ensure proper cleanup.
     */
    fun close() {
        httpClient.close()
    }

    companion object {
        private const val SOCKET_TIMEOUT = 5000L
        private const val CONN_TIMEOUT = 10000L
    }
}