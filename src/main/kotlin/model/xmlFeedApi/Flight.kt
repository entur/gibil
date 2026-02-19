package model.xmlFeedApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlTransient

@XmlAccessorType(XmlAccessType.FIELD)
class Flight {

    @XmlAttribute(name = "uniqueID")
    lateinit var uniqueID: String

    @XmlElement(name = "airline")
    var airline: String? = null

    @XmlElement(name = "flight_id")
    var flightId: String? = null

    @XmlElement(name = "dom_int")
    var domInt: String? = null

    @XmlElement(name = "schedule_time")
    var scheduleTime: String? = null

    @XmlElement(name = "arr_dep")
    var arrDep: String? = null

    @XmlElement(name = "airport")
    var airport: String? = null

    @XmlElement(name = "via_airport")
    var viaAirport: String? = null

    @XmlElement(name = "check_in")
    var checkIn: String? = null

    @XmlElement(name = "gate")
    var gate: String? = null

    @XmlElement(name = "status")
    var status: FlightStatus? = null

    @XmlElement(name = "delayed")
    var delayed: String? = null

    @XmlElement(name = "codeshareAirlineDesignators")
    var AirlineDesignators: String? = null

    @XmlElement(name = "codeshareAirlineNames")
    var AirlineNames: String? = null

    @XmlElement(name = "codeshareFlightNumbers")
    var FlightNumbers: String? = null

    @XmlElement(name = "codeshareOperationalSuffixs")
    var OperationalSuffixs: String? = null

    // These fields are populated when merging flight data from multiple airport queries.
    @XmlTransient
    var departureAirport: String? = null

    @XmlTransient
    var arrivalAirport: String? = null

    @XmlTransient
    var scheduledDepartureTime: String? = null

    @XmlTransient
    var scheduledArrivalTime: String? = null

    @XmlTransient
    var departureStatus: FlightStatus? = null

    @XmlTransient
    var arrivalStatus: FlightStatus? = null

    @XmlTransient
    var isMerged: Boolean = false

    //JAXB needs an empty constructor to populate the fields with the xml data
    constructor()

    //This constructor is not needed, but allows to make own instances
    constructor(uniqueID: String) {
        this.uniqueID = uniqueID
    }

    constructor(airline: String, airport: String) {
        this.airline = airline
        this.airport = airport
    }

    fun isDeparture(): Boolean = arrDep == "D"
    fun isArrival(): Boolean = arrDep == "A"

    fun isCancelled(): Boolean = status?.code == "C"
    fun isDelayed(): Boolean = delayed == "Y"

    /**
     * Populates the merged fields based on whether this flight is a departure or arrival.
     * For departures with via_airport, uses the first via_airport as the immediate destination.
     * For arrivals with via_airport, uses the last via_airport as the immediate origin.
     * Handles multiple via_airports separated by commas (e.g., "EVE,ANX").
     * Call this after parsing from XML before merging with other flights.
     * @param queryAirportCode The airport code used in the API query
     */
    fun populateMergedFields(queryAirportCode: String) {
        if (isDeparture()) {
            departureAirport = queryAirportCode
            // If via_airport exists, parse it and use the FIRST one as immediate next stop
            val via = viaAirport
            arrivalAirport = if (!via.isNullOrBlank()) {
                via.split(",").firstOrNull()?.trim() ?: airport
            } else {
                airport
            }
            scheduledDepartureTime = scheduleTime
            departureStatus = status
        } else {
            arrivalAirport = queryAirportCode
            // If via_airport exists, parse it (may be comma-separated) and use the LAST one as immediate previous stop
            val via = viaAirport
            departureAirport = if (!via.isNullOrBlank()) {
                via.split(",").lastOrNull()?.trim() ?: airport
            } else {
                airport
            }
            scheduledArrivalTime = scheduleTime
            arrivalStatus = status
        }
    }

    /**
     * Merges data from another Flight with the same uniqueID.
     * Combines departure data from one airport with arrival data from another.
     * @param other The other Flight to merge with (must have same uniqueID)
     * @return A new Flight with combined data from both
     */
    fun mergeWith(other: Flight): Flight {
        if (this.uniqueID != other.uniqueID) {
            throw IllegalArgumentException("Cannot merge flights with different uniqueIDs: ${this.uniqueID} vs ${other.uniqueID}")
        }

        return Flight(this.uniqueID).apply {
            // Basic fields - prefer non-null values
            airline = this@Flight.airline ?: other.airline
            flightId = this@Flight.flightId ?: other.flightId
            domInt = this@Flight.domInt ?: other.domInt
            viaAirport = this@Flight.viaAirport ?: other.viaAirport
            delayed = this@Flight.delayed ?: other.delayed
            AirlineDesignators = this@Flight.AirlineDesignators ?: other.AirlineDesignators
            AirlineNames = this@Flight.AirlineNames ?: other.AirlineNames
            FlightNumbers = this@Flight.FlightNumbers ?: other.FlightNumbers
            OperationalSuffixs = this@Flight.OperationalSuffixs ?: other.OperationalSuffixs

            // Merge departure data
            departureAirport = this@Flight.departureAirport ?: other.departureAirport
            scheduledDepartureTime = this@Flight.scheduledDepartureTime ?: other.scheduledDepartureTime
            departureStatus = this@Flight.departureStatus ?: other.departureStatus

            // Merge arrival data
            arrivalAirport = this@Flight.arrivalAirport ?: other.arrivalAirport
            scheduledArrivalTime = this@Flight.scheduledArrivalTime ?: other.scheduledArrivalTime
            arrivalStatus = this@Flight.arrivalStatus ?: other.arrivalStatus

            isMerged = true
        }
    }
}