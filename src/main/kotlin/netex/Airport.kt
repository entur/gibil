package org.example.netex

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlRootElement
import org.example.netex.FlightsContainer

@XmlRootElement(name = "airport")
@XmlAccessorType(XmlAccessType.FIELD)
class Airport {

    @XmlAttribute(name = "name")
    lateinit var name: String

    @XmlElement(name = "flights")
    var flightsContainer: FlightsContainer? = null

    //JAXB needs an empty constructor to populate the fields with the xml data
    constructor()

    //This constructor is not needed, but allows to make own instances
    constructor(name: String, flightsContainer: FlightsContainer? = null) {
        this.name = name
        this.flightsContainer = flightsContainer
    }
}