package org.gibil

import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Project-wide constants.
 * You can define top-level constants using `const val` or group them in an object.
 */


//AirportService
const val BATCH_SIZE = 5
const val REQUEST_DELAY_MS = 50

//AirportSizeClassification
val LARGE_AIRPORTS = setOf("OSL")
val MEDIUM_AIRPORTS = setOf("BGO", "BOO", "SVG", "TRD")

object Constants {
}

object AvinorApiConfig {
    const val TIME_FROM_MIN_NUM = 1
    const val TIME_FROM_MAX_NUM = 36
    const val TIME_FROM_DEFAULT = 2

    const val TIME_TO_MIN_NUM = 7
    const val TIME_TO_MAX_NUM = 336
    const val TIME_TO_DEFAULT = 10

    const val BASE_URL_AVINOR_XMLFEED = "https://asrv.avinor.no/XmlFeed/v1.0"
    const val BASE_URL_AVINOR_AIRPORT_NAMES = "https://asrv.avinor.no/airportNames/v1.0"
}

object LineSelector {
    val DEBUG_PRINTING_LINESELECTOR = false
}

//FindServiceJourney
object FindServicejourney {
    val DEBUG_PRINTING_FIND_SERVICEJ = false
    val LOCALE = Locale.ENGLISH
    val LOGGING_EVENTS_FIND_SERVICEJ = false
}

object ServiceJourneyModel {
    const val NETEX_NAMESPACE = "http://www.netex.org.uk/netex"
    val DEBUG_PRINTING_SJM = false
}

object Dates {
    //current date
    val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")
    val CURRENT_DATE = LocalDate.now().format(formatter)
}