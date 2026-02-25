package org.gibil.routes.entur

import org.gibil.service.ApiService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class StopPlaceApiHandler(
    private val apiService: ApiService,
    @Value("\${entur.api.base-url-stop-places}") private val baseUrlStopPlaces: String
) {

    companion object {
        const val TRANSPORT_MODE_AIR = "AIR"
        const val STOP_PLACE_TYPE_AIRPORT = "AIRPORT"
    }

    /**
     * Builds the URL for the StopPlaces API call with the specified parameters.
     * @param stopPlaceCount The number of stop places to retrieve, default is 100. If a non-positive number is provided, it defaults to 100.
     * @return A String representing the complete URL for the API call with query parameters.
     */
    fun stopPlaceApiUrlBuilder(stopPlaceCount: Int = 100): String {
        require(stopPlaceCount > 0) { "stopPlaceCount must be positive.(was $stopPlaceCount)" }
        return UriComponentsBuilder.fromUriString(baseUrlStopPlaces)
            .queryParam("count",stopPlaceCount)
            .queryParam("transportModes", TRANSPORT_MODE_AIR)
            .queryParam("stopPlaceTypes", STOP_PLACE_TYPE_AIRPORT)
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