package org.gibil

import okhttp3.OkHttpClient
import org.gibil.model.stopPlacesApi.Quay
import org.gibil.model.stopPlacesApi.Quays
import org.gibil.model.stopPlacesApi.StopPlace
import org.gibil.model.stopPlacesApi.StopPlaces
import org.gibil.service.ApiService
import util.SharedJaxbContext
import java.io.StringReader


object AirportQuayConfig {
    const val BASE_URL_STOP_PLACES = "https://api.entur.io/stop-places/v1/read/stop-places"
}

fun stopPlaceApiUrlBuilder(stopPlaceCount: Int = 10, transportModes: String = "AIR", stopPlaceTypes: String = "AIRPORT" ): String = buildString {
    append(AirportQuayConfig.BASE_URL_STOP_PLACES)
    if(stopPlaceCount > 0){
        append("?count=$stopPlaceCount")
    }
    append("&transportModes=$transportModes")
    append("&stopPlaceTypes=$stopPlaceTypes")
}

fun unmarhsallStopPlaceXml(xmlData: String): StopPlaces {
    try {
        val unmarshaller = SharedJaxbContext.createUnmarshaller()
        return unmarshaller.unmarshal(StringReader(xmlData)) as StopPlaces

    } catch (e: Exception) {
        throw RuntimeException("Error parsing StopPlaces", e)
    }
}

fun isolateIataCode(quay: Quay): String? {
    return quay.keyList?.keyValues
        ?.find { it.key == "imported-id" }
        ?.value
        ?.takeIf { it.startsWith("AVI:Quay:") }
        ?.removePrefix("AVI:Quay:")
}

fun main() {
    val url = stopPlaceApiUrlBuilder()
    println(url)
    val client = OkHttpClient()
    val apiService = ApiService(client)

    val response = apiService.apiCall(url, "application/xml")

    if(response != null) {
        val stopPlaces = unmarhsallStopPlaceXml(response)
        println("Found ${stopPlaces.stopPlace.size} stop places:")
        stopPlaces.stopPlace.forEach { sp ->
            println("  Type: ${sp.stopPlaceType}")
            sp.quays?.quay?.forEach { quay ->
                println("    Quay ID: ${quay.id}")
                val isolatedIataCode = isolateIataCode(quay)
                println("   IATA isolated ${isolatedIataCode}")
                quay.keyList?.keyValues?.forEach { kv ->
                    println("      ${kv.key}: ${kv.value}")
                }
            }

        }
    }

    
}
