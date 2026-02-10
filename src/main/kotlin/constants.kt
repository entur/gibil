package org.gibil

import java.util.Locale

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

object LineSelector {
    val DEBUG_PRINTING_LINESELECTOR = false
}

//FilterExtimeAndFindServiceJourney
object FilterExtimeAFSJ {
    val DEBUG_PRINTING_FEAFSJ = false
    val LOCALE = Locale.ENGLISH
}

object ServiceJourneyModel {
    const val NETEX_NAMESPACE = "http://www.netex.org.uk/netex"
    val DEBUG_PRINTING_SJM = false
}