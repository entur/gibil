package routes.api

import java.time.Instant


data class AvinorXmlFeedParams(
    val airportCode: String,
    val timeFrom: Int = AvinorApiConfig.TIME_FROM_DEFAULT,
    val timeTo: Int = AvinorApiConfig.TIME_TO_DEFAULT,
    val direction: String? = null,
    val lastUpdate: Instant? = null,
    val codeshare: Boolean = false
)

fun avinorXmlFeedUrlBuilder(params: AvinorXmlFeedParams): String = buildString {
    append(AvinorApiConfig.BASE_AVINOR_XMLFEED_URL)
    append("?airport=${params.airportCode.uppercase()}")
    append("&TimeFrom=${params.timeFrom}")
    append("TimeTo=${params.timeTo}")

    if(params.lastUpdate != null) {
        val lastUpdateString = params.lastUpdate.toString()
        append("&lastUpdate${lastUpdateString}")
    }

    if(params.direction != null) {
        append("&Direction${params.direction}")
    }

    //TODO FIND OUT IF WE NEED IT
    if(params.codeshare) {
        append("&codeshare=Y")
    }
}

fun main() {
    val xmlFeed = AvinorXmlFeedParams("OSL")
    println(avinorXmlFeedUrlBuilder(xmlFeed))
}