package util
import org.gibil.LARGE_AIRPORTS
import org.gibil.MEDIUM_AIRPORTS


object AirportSizeClassification {

    /**
     * Returns size priority for specified airport.
     *
     * @param airportCode the IATA code for airport which size is checked
     * @return A priority value depending on size. 3-Large, 2-Medium, 1-Small
     */
    fun getSizePriority(airportCode: String): Int = when(airportCode.uppercase()) {
        in LARGE_AIRPORTS -> 3
        in MEDIUM_AIRPORTS -> 2
        else -> 1
    }

    /**
     * Orders two airports by which is counted as larger specified by three sizes. Large, Medium and Small.
     * If Airport is not in the [LARGE_AIRPORTS] or [MEDIUM_AIRPORTS] the airport is counted as small.
     * Used when sizing priority of airports is needed.
     *
     * @param requestingAirportCode String. The first airport IATA code. Often the one used in the API call
     * @param flight String. The second airport IATA code. Often the one provided by flight.airport
     * @return A pair with the larger higher-priority airport as [Pair.first] and the lower priority airport as [Pair.second]
     *          If same size [requestingAirportCode] comes first
     */
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