package model.serviceJourney

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
data class TimetabledPassingTime(
    @field:XmlElement(name = "DepartureTime", namespace = "http://www.netex.org.uk/netex")
    var departureTime: String? = null,

    @field:XmlElement(name = "ArrivalTime", namespace = "http://www.netex.org.uk/netex")
    var arrivalTime: String? = null
)