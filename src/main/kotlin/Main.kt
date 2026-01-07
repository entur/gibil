package org.example


import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response



fun main() {
    val client = OkHttpClient()

    val request = Request.Builder()
        .url("https://asrv.avinor.no/XmlFeed/v1.0?TimeFrom=1&TimeTo=7&airport=OSL&direction=D&lastUpdate=2024-08-08T09:30:00Z")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("Request failed: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (response.isSuccessful) {
                    println("Response: ${response.body?.string()}")
                } else {
                    println("Error: ${response.code}")
                }
            }
        }
    })

    Thread.sleep(3000) // Wait for async response
}