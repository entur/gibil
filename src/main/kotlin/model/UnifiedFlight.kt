package model

import java.time.LocalDateTime
import java.time.LocalDate

/**
 * Represents a single visit to an airport within a flight chain.
 * Each stop can have an arrival (incoming leg) and/or departure (outgoing leg).
 *
 * Status fields are split into departure and arrival because at intermediate stops
 * (e.g. BOO in TOS→BOO→SVJ), the arrival and departure come from separate Avinor records
 * with independent status codes (e.g. arrival may be "A"/arrived while departure is "E"/delayed).
 *
 * Avinor status codes: "A" = arrived, "D" = departed, "E" = new estimated time,
 * "C" = cancelled, "N" = new/on-time.
 */
data class FlightStop(
    val airportCode: String,
    val arrivalTime: LocalDateTime?,
    val departureTime: LocalDateTime?,
    val departureStatusCode: String? = null,
    val departureStatusTime: LocalDateTime? = null,
    val arrivalStatusCode: String? = null,
    val arrivalStatusTime: LocalDateTime? = null,
    val targetAirport: String? = null // Next airport in the chain (used for gap detection)
)

/**
 * Represents a complete journey (chain of stops) for a single Flight ID.
 * Handles both Direct (2 stops) and Multi-Leg (3+ stops) flights uniformly.
 */
data class UnifiedFlight(
    val flightId: String,
    val operator: String,
    val date: LocalDate,
    val stops: List<FlightStop>
) {
    // Convenience accessors
    val origin: String get() = stops.first().airportCode
    val destination: String get() = stops.last().airportCode
    val isMultiLeg: Boolean get() = stops.size > 2
}
