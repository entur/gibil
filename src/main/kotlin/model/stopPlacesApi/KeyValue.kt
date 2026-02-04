package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement

@XmlAccessorType(XmlAccessType.FIELD)
class KeyValue {
    @field:XmlElement(name = "Key")
    val key: String = ""

    @field:XmlElement(name = "Value")
    val value: String = ""
}