package org.gibil.routes.api

import org.gibil.service.ApiService
import org.springframework.stereotype.Component

@Component
class StopPlaceApiHandler(private val apiService: ApiService) {

    object StopPlaceApiHandlerConfig {
        const val BASE_URL_STOP_PLACES = "https://api.entur.io/stop-places/v1/read/stop-places"
    }

    /**
     * Builds url used for StopPlaces [API] from Entur, defaults to 100 airports,
     * [AIR] transportModes, [AIRPORT] stop place type. Other functions must be expanded to use other modes
     * @param stopPlaceCount Int, [transportModes] String, [stopPlaceTypes] String
     * @return String, built url
     */
    fun stopPlaceApiUrlBuilder(stopPlaceCount: Int = 100, transportModes: String = "AIR", stopPlaceTypes: String = "AIRPORT"): String = buildString {
        append(StopPlaceApiHandlerConfig.BASE_URL_STOP_PLACES)
        if(stopPlaceCount > 0){
            append("?count=$stopPlaceCount")
        }
        append("&transportModes=$transportModes")
        append("&stopPlaceTypes=$stopPlaceTypes")
    }

    /**
     * Makes use of [ApiService] to make the API call to Enturs StopPlaces Api
     * Asks for XML data
     * @return String?, XML response from API
     */
    fun fetchAirportStopPlaces(): String? {
        val url = stopPlaceApiUrlBuilder()
        return apiService.apiCall(url, "application/xml")
    }
}