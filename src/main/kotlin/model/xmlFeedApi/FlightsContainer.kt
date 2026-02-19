package model.xmlFeedApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement

@XmlAccessorType(XmlAccessType.FIELD)
class FlightsContainer {

    @XmlAttribute(name = "lastUpdate")
    var lastUpdate: String? = null

    @XmlElement(name = "flight")
    var flight: MutableList<Flight> = mutableListOf()

    //JAXB needs an empty constructor to populate the fields with the xml data
    constructor()
}