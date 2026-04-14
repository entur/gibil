package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement

@XmlAccessorType(XmlAccessType.FIELD)
class StopPlace {
    @field:XmlElement(name = "StopPlaceType", namespace = "http://www.netex.org.uk/netex")
    val stopPlaceType: String = ""

    @field:XmlElement(name = "quays", namespace = "http://www.netex.org.uk/netex")
    val quays: Quays? = null
}