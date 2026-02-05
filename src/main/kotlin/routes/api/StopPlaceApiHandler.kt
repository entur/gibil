package org.gibil.routes.api

import org.gibil.service.ApiService
import org.springframework.stereotype.Component

@Component
class StopPlaceApiHandler(private val apiService: ApiService) {

    object StopPlaceApiHandlerConfig {
        const val BASE_URL_STOP_PLACES = "https://api.entur.io/stop-places/v1/read/stop-places"
    }

    fun stopPlaceApiUrlBuilder(stopPlaceCount: Int = 100, transportModes: String = "AIR", stopPlaceTypes: String = "AIRPORT"): String = buildString {
        append(StopPlaceApiHandlerConfig.BASE_URL_STOP_PLACES)
        if(stopPlaceCount > 0){
            append("?count=$stopPlaceCount")
        }
        append("&transportModes=$transportModes")
        append("&stopPlaceTypes=$stopPlaceTypes")
    }

    fun fetchAirportStopPlaces(): String? {
        val url = stopPlaceApiUrlBuilder()
        return apiService.apiCall(url, "application/xml")
    }
}