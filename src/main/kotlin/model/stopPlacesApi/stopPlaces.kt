package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.*

@XmlRootElement(name = "stopPlaces")
@XmlAccessorType(XmlAccessType.FIELD)
class StopPlaces {
    @field:XmlElement(name = "StopPlace")
    val stopPlaces: List<StopPlace> = emptyList()
}

@XmlAccessorType(XmlAccessType.FIELD)
class StopPlace {
    @field:XmlElement(name = "StopPlaceType")
    val stopPlaceType: String = ""

    @field:XmlElement(name = "quays")
    val quays: Quays? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
class Quays {
    @field:XmlElement(name = "Quay")
    val quays: List<Quay> = emptyList()
}

@XmlAccessorType(XmlAccessType.FIELD)
class Quay {
    @field:XmlAttribute
    val id: String = ""

    @field:XmlElement(name = "keyList")
    val keyList: KeyList? = null
}

@XmlAccessorType(XmlAccessType.FIELD)
class KeyList {
    @field:XmlElement(name = "KeyValue")
    val keyValues: List<KeyValue> = emptyList()
}

@XmlAccessorType(XmlAccessType.FIELD)
class KeyValue {
    @field:XmlElement(name = "Key")
    val key: String = ""

    @field:XmlElement(name = "Value")
    val value: String = ""
}