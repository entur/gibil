package subscriptiontest.service

import model.FlightStop
import model.UnifiedFlight
import java.time.Instant
import java.time.LocalDate

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
            arrivalTime = Instant.parse("2026-02-26T10:00:00Z"),
            departureTime = Instant.parse("2026-02-26T11:00:00Z"),
            departureStatusCode = departureStatusCode,
            departureStatusTime = null,
            arrivalStatusCode = arrivalStatusCode,
            arrivalStatusTime = null
        )
    }
}