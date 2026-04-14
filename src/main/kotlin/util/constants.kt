package org.gibil.util

import java.time.Duration
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

    // Svalbard is classified as international (domInt="I") by Avinor, but should be treated as domestic
    const val SVALBARD_AIRPORTS = "LYR"
}

object QuayCodes {
    const val DEFAULT_KEY = "DEFAULT"
}

object SiriConfig {
    const val SIRI_VERSION_DELIVERY = "2.1"
}

object FindServiceJourneyPaths {
    //base path when running on a local computer, and not in cloud
    val LOCAL_BASEPATH = "src/main/resources/extimeData"
    val CLOUD_BASEPATH = "/tmp/netex_data"
}

object TiamatImportPaths {
    val LOCAL_BASEPATH = "src/main/resources/stopPlaceData" //TODO THIS ONE IS PLACEHOLDER
    val CLOUD_BASEPATH = "/tmp/stop_place_data"
}

object AvinorApiConfig {
    const val TIME_FROM_MIN_NUM = 1
    const val TIME_FROM_MAX_NUM = 36
    const val TIME_FROM_DEFAULT = 5

    const val TIME_TO_MIN_NUM = 7
    const val TIME_TO_MAX_NUM = 336
    const val TIME_TO_DEFAULT = 24

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


    fun daytypeBuilder(zoneDateTime: ZonedDateTime): String{
        val norwayZone = ZoneId.of("Europe/Oslo")

        val norwayDateTimeDeparture = zoneDateTime .withZoneSameInstant(norwayZone)

        val formatMonth = DateTimeFormatter.ofPattern("MMM", LOCALE)
        val formatDayShortName = DateTimeFormatter.ofPattern("E", LOCALE)
        val formatDayNum = DateTimeFormatter.ofPattern("dd", LOCALE)

        // Implement formats onto object and create partial daytyperef-value
        val month = norwayDateTimeDeparture.format(formatMonth)
        val dayName = norwayDateTimeDeparture.format(formatDayShortName)
        val dayNum = norwayDateTimeDeparture.format(formatDayNum)

        val daytype = "${month}_${dayName}_${dayNum}"

        return daytype
    }

    fun tomorrowDaytype(): String{
        val time = instantNowUtc().plus(Duration.ofHours(24))
        val daytype = daytypeBuilder(time)

        return daytype
    }

}