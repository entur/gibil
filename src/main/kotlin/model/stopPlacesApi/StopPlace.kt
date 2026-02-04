package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement

@XmlAccessorType(XmlAccessType.FIELD)
class StopPlace {
    @field:XmlElement(name = "StopPlaceType")
    val stopPlaceType: String = ""

    @field:XmlElement(name = "quays")
    val quays: Quays? = null
}