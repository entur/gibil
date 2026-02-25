package model.xmlFeedApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlTransient
import org.gibil.FlightCodes

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

    val viaAirports: List<String>
        get() = viaAirport?.split(",")?.map { it.trim() } ?: emptyList()

    @XmlElement(name = "check_in")
    var checkIn: String? = null

    @XmlElement(name = "gate")
    var gate: String? = null

    @XmlElement(name = "status")
    var status: FlightStatus? = null

    @XmlElement(name = "delayed")
    var delayed: String? = null

    @XmlElement(name = "codeshareAirlineDesignators")
    var airlineDesignators: String? = null

    @XmlElement(name = "codeshareAirlineNames")
    var airlineNames: String? = null

    @XmlElement(name = "codeshareFlightNumbers")
    var flightNumbers: String? = null

    @XmlElement(name = "codeshareOperationalSuffixs")
    var operationalSuffixs: String? = null

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

    //[Direction] from Avinor
    fun isDeparture(): Boolean = arrDep == FlightCodes.DEPARTURE_CODE
    fun isArrival(): Boolean = arrDep == FlightCodes.ARRIVAL_CODE

    //[Satus Code] from Avinor
    fun isCancelled(): Boolean = status?.code == FlightCodes.CANCELLED_CODE
    fun isDeparted(): Boolean = status?.code == FlightCodes.DEPARTED_CODE
    fun isArrived(): Boolean = status?.code == FlightCodes.ARRIVED_CODE
    fun isNewTime(): Boolean = status?.code == FlightCodes.NEW_TIME_CODE
    fun isNewInfo(): Boolean = status?.code == FlightCodes.NEW_INFO_CODE

    //[Dom_int] from Avinor
    fun isDomestic(): Boolean = domInt == FlightCodes.DOMESTIC_CODE
    fun isInternational(): Boolean = domInt == FlightCodes.INTERNATIONAL_CODE
    fun isSchengen(): Boolean = domInt == FlightCodes.SCHENGEN_CODE

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
}