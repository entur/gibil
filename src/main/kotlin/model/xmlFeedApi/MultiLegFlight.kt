package model.xmlFeedApi

//Represents complete multi-leg flight journeys
data class MultiLegFlight (
    val flightId: String,
    val airline: String,
    val legs: List<FlightLeg>,
    val originAirport: String,
    val destinationAirport: String,
    val scheduledDepartureTime: String?,
    val viaAirports:List<String>
) {
    val totalLegs: Int get() = legs.size
    val allStops: List<String> get() = listOf(originAirport) + viaAirports + listOf(destinationAirport)

    val originDepartureTime: String? get() = legs.firstOrNull()?.scheduledDepartureTime
    val destinationArrivalTime: String? get() = legs.lastOrNull()?.scheduledDepartureTime
}

//Represents a single leg of a multi-leg flight
data class FlightLeg (
    val legNumber: Int,
    val departureAirport: String,
    val arrivalAirport: String,
    val scheduledDepartureTime: String?,
    val scheduledArrivalTime: String?,
    val expectedDepartureTime: String?,
    val expectedArrivalTime: String?,
    val departureStatus: FlightStatus?,
    val arrivalStatus: FlightStatus?,
    val uniqueId: String
)