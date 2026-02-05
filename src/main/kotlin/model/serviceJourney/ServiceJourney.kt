package model.serviceJourney

import jakarta.xml.bind.annotation.*

@XmlRootElement(name = "ServiceJourney", namespace = "http://www.netex.org.uk/netex")
@XmlAccessorType(XmlAccessType.FIELD)
data class ServiceJourney(

    @field:XmlAttribute(name = "id")
    var serviceJourneyId: String = "",

    @field:XmlElementWrapper(name = "dayTypes", namespace = "http://www.netex.org.uk/netex")
    @field:XmlElement(name = "DayTypeRef", namespace = "http://www.netex.org.uk/netex")
    private var dayTypeRefs: MutableList<DayTypeRef> = mutableListOf(),

    @field:XmlElement(name = "PublicCode", namespace = "http://www.netex.org.uk/netex")
    var publicCode: String = "",

    @field:XmlElement(name = "passingTimes", namespace = "http://www.netex.org.uk/netex")
    private var passingTimesWrapper: PassingTimesWrapper? = null

) {
    // Computed property to get list of dayType strings
    val dayTypes: List<String>
        get() = dayTypeRefs.map { it.ref }

    // Computed property to get departure time
    val departureTime: String
        get() {
            val times = passingTimesWrapper?.timetabledPassingTimes ?: emptyList()
            return times.firstOrNull { it.departureTime != null }?.departureTime ?: ""
        }

    // Computed property to get arrival time
    val arrivalTime: String
        get() {
            val times = passingTimesWrapper?.timetabledPassingTimes ?: emptyList()
            return times.lastOrNull { it.arrivalTime != null }?.arrivalTime ?: ""
        }
}

@XmlAccessorType(XmlAccessType.FIELD)
data class PassingTimesWrapper(
    @field:XmlElement(name = "TimetabledPassingTime", namespace = "http://www.netex.org.uk/netex")
    var timetabledPassingTimes: MutableList<TimetabledPassingTime> = mutableListOf()
)