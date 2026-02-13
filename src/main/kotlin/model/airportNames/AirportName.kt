package model.airportNames

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
class AirportName {
    @field:XmlAttribute(name = "code")
    val code: String? = null

    @field:XmlAttribute(name = "name")
    val name: String? = null
}