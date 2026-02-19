package org.gibil

import java.time.Instant
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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

val SIRI_VERSION_DELIVERY = "2.1"

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

//FindServiceJourney
object FindServicejourney {
    val DEBUG_PRINTING_FIND_SERVICEJ = false
    val LOGGING_EVENTS_FIND_SERVICEJ = false
}

object ServiceJourneyModel {
    const val NETEX_NAMESPACE = "http://www.netex.org.uk/netex"
    val DEBUG_PRINTING_SJM = false
}

object Dates {
    val LOCALE = Locale.ENGLISH

    val formats = mapOf<String, DateTimeFormatter>(
        "MMMM_dd_yyyy" to DateTimeFormatter.ofPattern("MMMM dd, yyyy", LOCALE),
        "yyyy_MM_dd" to DateTimeFormatter.ofPattern("yyyyMMdd", LOCALE)
    )

    val CURRENT_DATE_MMMddyyyy = LocalDate.now().format(formats["MMMM_dd_yyyy"])

    val CURRENT_DATE_yyyyMMdd = LocalDate.now().format(formats["yyyy_MM_dd"])

    val INSTANT_NOW_ZONEDATETIME = ZonedDateTime.now(java.time.ZoneOffset.UTC)

    val INSTANT_NOW_SYSTEMDEFAULT = Instant.now().atZone(ZoneId.systemDefault())
}