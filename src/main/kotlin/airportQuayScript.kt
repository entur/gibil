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

fun makeAirportQuayMapFromStopPlaces(stopPlaces: StopPlaces): Map<String, List<String>> {
    return stopPlaces.stopPlace
        .flatMap { sp -> sp.quays?.quay ?: emptyList() }
        .mapNotNull { quay ->
            isolateIataCode(quay)?.let { iataCode ->
                iataCode to quay.id
            }
        }
        .groupBy({it.first}, {it.second})
}

fun main() {
    val url = stopPlaceApiUrlBuilder()
    println(url)
    val client = OkHttpClient()
    val apiService = ApiService(client)

    val response = apiService.apiCall(url, "application/xml")

    if (response != null) {
        val stopPlaces = unmarhsallStopPlaceXml(response)

        val iataToQuayMap = makeAirportQuayMapFromStopPlaces(stopPlaces)

        println("Found ${iataToQuayMap.size} IATA to Quay mappings:")
        iataToQuayMap.forEach { (iata, quayId) ->
            println("  $iata -> $quayId")
        }
    }
}