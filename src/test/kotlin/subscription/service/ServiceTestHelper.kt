package subscriptiontest.service

import model.FlightStop
import model.UnifiedFlight
import java.time.LocalDate
import java.time.LocalDateTime

object ServiceTestHelper {

    fun mockFlight(
        flightId: String,
        date: String,
        stops: List<FlightStop> = listOf(defaultStop())
    ): UnifiedFlight {
        return UnifiedFlight(
            flightId = flightId,
            operator = "WF",
            date = LocalDate.parse(date),
            stops = stops
        )
    }

    fun defaultStop(
        airportCode: String = "OSL",
        departureStatusCode: String? = "N",
        arrivalStatusCode: String? = "N"
    ): FlightStop {
        return FlightStop(
            airportCode = airportCode,
            arrivalTime = LocalDateTime.of(2026, 2, 26, 10, 0),
            departureTime = LocalDateTime.of(2026, 2, 26, 11, 0),
            departureStatusCode = departureStatusCode,
            departureStatusTime = null,
            arrivalStatusCode = arrivalStatusCode,
            arrivalStatusTime = null
        )
    }
}