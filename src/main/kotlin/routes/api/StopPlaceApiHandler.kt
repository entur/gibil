package org.gibil.routes.api

import org.gibil.service.ApiService
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class StopPlaceApiHandler(private val apiService: ApiService) {

    companion object {
        const val BASE_URL_STOP_PLACES = "https://api.entur.io/stop-places/v1/read/stop-places"
        const val STOP_PLACE_BASE_COUNT = 100
    }

    /**
     * Builds url used for StopPlaces [API] from Entur, defaults to 100 airports,
     * [AIR] transportModes, [AIRPORT] stop place type. Other functions must be expanded to use other modes
     * @param stopPlaceCount Int, [transportModes] String, [stopPlaceTypes] String
     * @return String, built url
     */
    fun stopPlaceApiUrlBuilder(
        stopPlaceCount: Int = STOP_PLACE_BASE_COUNT,
        transportModes: String = "AIR",
        stopPlaceTypes: String = "AIRPORT"
    ): String {
        require(stopPlaceCount > 0) { "stopPlaceCount must be positive" }

        return UriComponentsBuilder.fromUriString(BASE_URL_STOP_PLACES)
            .queryParam("count", stopPlaceCount)
            .queryParam("transportModes", transportModes)
            .queryParam("stopPlaceTypes", stopPlaceTypes)
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