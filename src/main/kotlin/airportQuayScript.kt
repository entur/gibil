package org.gibil


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