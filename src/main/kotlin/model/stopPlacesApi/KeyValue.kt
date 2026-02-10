package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement

@XmlAccessorType(XmlAccessType.FIELD)
class KeyValue {
    @field:XmlElement(name = "Key", namespace = "http://www.netex.org.uk/netex")
    val key: String = ""

    @field:XmlElement(name = "Value", namespace = "http://www.netex.org.uk/netex")
    val value: String = ""
}