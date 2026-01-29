package util

object AirportSizeClassification {
    //Might serve better to declare these somewhere else?
    private val LARGE_AIRPORTS = setOf("OSL")
    private val MEDIUM_AIRPORTS = setOf("BGO", "BOO", "SVG", "TRD")

    fun getSizePriority(airportCode: String): Int = when(airportCode.uppercase()) {
        in LARGE_AIRPORTS -> 3
        in MEDIUM_AIRPORTS -> 2
        else -> 1
    }

    fun orderAirportBySize(requestingAirportCode: String, flight: String): Pair<String, String> {
        val requestingAirportCodeSize = getSizePriority(requestingAirportCode)
        val flightSize = getSizePriority(flight)

        return if(requestingAirportCodeSize >= flightSize) {
            requestingAirportCode to flight
        } else {
            flight to requestingAirportCode
        }
    }
}