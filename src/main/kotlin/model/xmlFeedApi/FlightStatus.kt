package model.xmlFeedApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute

@XmlAccessorType(XmlAccessType.FIELD)
class FlightStatus {

    @XmlAttribute(name = "code")
    var code: String? = null

    @XmlAttribute(name = "time")
    var time: String? = null

    //JAXB needs an empty constructor to populate the fields with the xml data
    constructor()
}