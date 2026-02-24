package util
import org.gibil.AirportSizeConfig


object AirportSizeClassification {

    /**
     * Returns size priority for specified airport.
     *
     * @param airportCode the IATA code for airport which size is checked
     * @return A priority value depending on size. 3-Large, 2-Medium, 1-Small
     */
    fun getSizePriority(airportCode: String): Int = when(airportCode.uppercase()) {
        in AirportSizeConfig.LARGE_AIRPORTS -> 3
        in AirportSizeConfig.MEDIUM_AIRPORTS -> 2
        else -> 1
    }

    fun orderAirportsBySize(airports: List<String>): List<String> {
        return airports.sortedByDescending { getSizePriority(it) }
    }
}