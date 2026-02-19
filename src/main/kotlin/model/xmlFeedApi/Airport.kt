package model.xmlFeedApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlRootElement

@XmlRootElement(name = "airport")
@XmlAccessorType(XmlAccessType.FIELD)
class Airport {

    @XmlAttribute(name = "name")
    lateinit var name: String

    @XmlElement(name = "flights")
    var flightsContainer: FlightsContainer? = null

    //JAXB needs an empty constructor to populate the fields with the xml data
    constructor()

    //TODO SHOULD LIKELY BE REMOVED, CHANGE TESTS TO MAKE USE OF MOCKK
    constructor(name: String, flightsContainer: FlightsContainer? = null) {
        this.name = name
        this.flightsContainer = flightsContainer
    }
}