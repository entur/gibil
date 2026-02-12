package org.gibil.routes.api

import org.gibil.service.ApiService
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder


@Component
class StopPlaceApiHandler(private val apiService: ApiService) {

    object StopPlaceApiHandlerConfig {
        const val BASE_URL_STOP_PLACES = "https://api.entur.io/stop-places/v1/read/stop-places"
        const val TRANSPORT_MODE_AIR = "AIR"
        const val STOP_PLACE_TYPE_AIRPORT = "AIRPORT"
    }

    /**
     * Builds url used for StopPlaces [API] from Entur, defaults to 100 airports,
     * [AIR] transportModes, [AIRPORT] stop place type. Other functions must be expanded to use other modes
     * @param stopPlaceCount Int, [transportModes] String, [stopPlaceTypes] String
     * @return String, built url
     */
    open fun stopPlaceApiUrlBuilder(stopPlaceCount: Int = 100): String {
        return UriComponentsBuilder.fromUriString(StopPlaceApiHandlerConfig.BASE_URL_STOP_PLACES)
            .queryParam("count", if (stopPlaceCount > 0) stopPlaceCount else 100)
            .queryParam("transportModes", StopPlaceApiHandlerConfig.TRANSPORT_MODE_AIR)
            .queryParam("stopPlaceTypes", StopPlaceApiHandlerConfig.STOP_PLACE_TYPE_AIRPORT)
            .build()
            .toUriString()
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