package model.serviceJourney

import jakarta.xml.bind.annotation.*
import org.gibil.ServiceJourneyModel

@XmlRootElement(name = "ServiceJourney", namespace = ServiceJourneyModel.NETEX_NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
data class ServiceJourney(

    @field:XmlAttribute(name = "id")
    var serviceJourneyId: String = "",

    @field:XmlElementWrapper(name = "dayTypes", namespace = ServiceJourneyModel.NETEX_NAMESPACE)
    @field:XmlElement(name = "DayTypeRef", namespace = ServiceJourneyModel.NETEX_NAMESPACE)
    private var dayTypeRefs: MutableList<DayTypeRef> = mutableListOf(),

    @field:XmlElement(name = "PublicCode", namespace = ServiceJourneyModel.NETEX_NAMESPACE)
    var publicCode: String = "",

    @field:XmlElement(name = "passingTimes", namespace = ServiceJourneyModel.NETEX_NAMESPACE)
    private var passingTimesWrapper: PassingTimesWrapper? = null

) {
    // Computed property to get list of dayType strings
    val dayTypes: List<String>
        get() = dayTypeRefs.map { it.ref }

    // Computed property to get departure time
    val departureTime: List<String>
        get() {
            val times = passingTimesWrapper?.timetabledPassingTimes ?: emptyList()
            return times.mapNotNull { it.departureTime }
        }

    // Computed property to get arrival time
    val arrivalTime: List<String>
        get() {
            val times = passingTimesWrapper?.timetabledPassingTimes ?: emptyList()
            return times.mapNotNull { it.arrivalTime }
        }
}

@XmlAccessorType(XmlAccessType.FIELD)
data class PassingTimesWrapper(
    @field:XmlElement(name = "TimetabledPassingTime", namespace = ServiceJourneyModel.NETEX_NAMESPACE)
    var timetabledPassingTimes: MutableList<TimetabledPassingTime> = mutableListOf()
)