package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.*

@XmlRootElement(name = "stopPlaces", namespace = "http://www.netex.org.uk/netex")
@XmlAccessorType(XmlAccessType.FIELD)
class StopPlaces {
    @field:XmlElement(name = "StopPlace", namespace = "http://www.netex.org.uk/netex")
    val stopPlace: MutableList<StopPlace> = mutableListOf()
}