package model.serviceJourney

import jakarta.xml.bind.annotation.*
import org.gibil.ServiceJourneyModel

@XmlAccessorType(XmlAccessType.FIELD)
data class TimetabledPassingTime(
    @field:XmlElement(name = "DepartureTime", namespace = ServiceJourneyModel.NETEX_NAMESPACE)
    var departureTime: String? = null,

    @field:XmlElement(name = "ArrivalTime", namespace = ServiceJourneyModel.NETEX_NAMESPACE)
    var arrivalTime: String? = null
)