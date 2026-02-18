package org.gibil.service

import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException

@Service
class ApiService(private val client: OkHttpClient) {

    /**
     * A basic api call that returns the raw XML it gets from the call.
     * Works only on open(public) level api's
     * @param url the complete url which the api-call is based on
     * @param acceptHeader Can input header if needed to change requested dataformat
     */
     fun apiCall(url: String, acceptHeader: String? = null): String? {
        val requestBuilder = Request.Builder().url(url)
        if(acceptHeader != null) {
            requestBuilder.addHeader("Accept", acceptHeader)
        }
        val request = requestBuilder.build()

        val response = client.newCall(request).execute()

        response.use {
            return if (response.isSuccessful) {
                response.body?.string()  // Returns raw XML
            } else {
                throw IOException("Error: ${response.code}")
            }
        }
    }

    fun apiCallToFile(url: String, targetFile: File, acceptHeader: String? = null) {
        val requestBuilder = Request.Builder().url(url)
        if(acceptHeader != null) {
            requestBuilder.addHeader("Accept", acceptHeader)
        }
        val request = requestBuilder.build()

        val response = client.newCall(request).execute()

        response.use {
            if(response.isSuccessful) {
                response.body?.byteStream()?.use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } else {
                throw IOException("Error: ${response.code}")
            }
        }
    }
}