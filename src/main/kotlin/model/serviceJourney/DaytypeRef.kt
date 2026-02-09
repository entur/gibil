package model.serviceJourney
import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
data class DayTypeRef(
    @field:XmlAttribute(name = "ref")
    var ref: String = ""
)