package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlElement

@XmlAccessorType(XmlAccessType.FIELD)
class Quay {
    @field:XmlAttribute
    val id: String = ""

    @field:XmlElement(name = "keyList")
    val keyList: KeyList? = null
}