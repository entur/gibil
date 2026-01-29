package subscription

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import uk.org.siri.siri21.Siri
import util.SharedJaxbContext
import java.io.StringWriter

class HttpHelper(
    private val verbose: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(HttpHelper::class.java)

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = CONN_TIMEOUT
            requestTimeoutMillis = CONN_TIMEOUT
            socketTimeoutMillis = SOCKET_TIMEOUT
        }
    }

    suspend fun postHeartbeat(address: String, requestorRef: String): Int {
        val siri = SiriHelper.createHeartbeatNotification(requestorRef)
        return postData(address, toXml(siri))
    }

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

    private fun toXml(siri: Siri): String {
        val marshaller = SharedJaxbContext.createMarshaller(true)
        val writer = StringWriter()
        marshaller.marshal(siri, writer)
        return writer.toString()
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        private const val SOCKET_TIMEOUT = 5000L
        private const val CONN_TIMEOUT = 10000L
    }
}