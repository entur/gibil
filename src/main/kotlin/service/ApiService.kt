package org.gibil.service

import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import java.io.IOException

@Service
open class ApiService(private val client: OkHttpClient) {

    /**
     * A basic api call that returns the raw XML it gets from the call.
     * Works only on open(public) level api's
     * @param url the complete url which the api-call is based on
     */
    open fun apiCall(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()

        response.use {
            return if (response.isSuccessful) {
                response.body?.string()  // Returns raw XML
            } else {
                throw IOException("Error: ${response.code}")
            }
        }
    }
}