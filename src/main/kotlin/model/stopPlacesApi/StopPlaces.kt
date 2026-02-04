package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.*

@XmlRootElement(name = "stopPlaces")
@XmlAccessorType(XmlAccessType.FIELD)
class StopPlaces {
    @field:XmlElement(name = "StopPlace")
    val stopPlaces: List<StopPlace> = emptyList()
}