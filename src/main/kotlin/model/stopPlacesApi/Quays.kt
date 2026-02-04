package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement

@XmlAccessorType(XmlAccessType.FIELD)
class Quays {
    @field:XmlElement(name = "Quay")
    val quays: List<Quay> = emptyList()
}