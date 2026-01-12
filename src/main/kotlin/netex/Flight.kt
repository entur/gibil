package org.example.netex

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import org.example.netex.FlightStatus

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

    //JAXB needs an empty constructor to populate the fields with the xml data
    constructor()

    //This constructor is not needed, but allows to make own instances
    constructor(uniqueID: String) {
        this.uniqueID = uniqueID
    }

}