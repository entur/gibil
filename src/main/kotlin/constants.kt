package org.gibil

import java.time.Instant
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Project-wide constants.
 * You can define top-level constants using `const val` or group them in an object.
 */


object PollingConfig {
    const val BATCH_SIZE = 5
    const val REQUEST_DELAY_MS = 50
}

object FlightCodes {
    //[Direction] from Avinor
    const val DEPARTURE_CODE = "D"
    const val ARRIVAL_CODE = "A"

    //[Satus Code] from Avinor
    const val ARRIVED_CODE = "A"
    const val CANCELLED_CODE = "C"
    const val DEPARTED_CODE = "D"
    const val NEW_TIME_CODE = "E"
    const val NEW_INFO_CODE = "N"

    //[Dom_int] from Avinor
    const val DOMESTIC_CODE = "D"
    const val INTERNATIONAL_CODE = "I"
    const val SCHENGEN_CODE = "S"
}

object AirportSizeConfig {
    val LARGE_AIRPORTS = setOf("OSL")
    val MEDIUM_AIRPORTS = setOf("BGO", "BOO", "SVG", "TRD")
}

object SiriConfig {
    const val SIRI_VERSION_DELIVERY = "2.1"
}

// Svalbard is classified as international (domInt="I") by Avinor, but should be treated as domestic
val SVALBARD_AIRPORTS = setOf("LYR")

object FindServiceJourneyConstants {
    //base path when running on a local computer, and not in cloud
    val LOCAL_BASEPATH = "src/main/resources/extimeData"

    val CLOUD_BASEPATH = "/app/extimeData"
}

object AvinorApiConfig {
    const val TIME_FROM_MIN_NUM = 1
    const val TIME_FROM_MAX_NUM = 36
    const val TIME_FROM_DEFAULT = 2

    const val TIME_TO_MIN_NUM = 7
    const val TIME_TO_MAX_NUM = 336
    const val TIME_TO_DEFAULT = 10

}

object ServiceJourneyModel {
    const val NETEX_NAMESPACE = "http://www.netex.org.uk/netex"
}

object Dates {
    val LOCALE = Locale.ENGLISH

    val formats = mapOf<String, DateTimeFormatter>(
        "MMMM_dd_yyyy" to DateTimeFormatter.ofPattern("MMMM dd, yyyy", LOCALE),
        "yyyy_MM_dd" to DateTimeFormatter.ofPattern("yyyyMMdd", LOCALE)
    )

    fun currentDateMMMddyyyy() = LocalDate.now().format(formats["MMMM_dd_yyyy"])
    fun instantNowUtc(): ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
    fun instantNowSystemDefault(): ZonedDateTime = Instant.now().atZone(ZoneId.of("Europe/Oslo"))
}