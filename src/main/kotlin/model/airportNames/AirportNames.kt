package model.airportNames

import jakarta.xml.bind.annotation.*

@XmlRootElement(name = "airportNames")
@XmlAccessorType(XmlAccessType.FIELD)
class AirportNames {
    @field:XmlElement(name = "airportName")
    var airportName: MutableList<AirportName> = mutableListOf()
}