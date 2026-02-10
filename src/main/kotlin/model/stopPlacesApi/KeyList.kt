package org.gibil.model.stopPlacesApi

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement

@XmlAccessorType(XmlAccessType.FIELD)
class KeyList {
    @field:XmlElement(name = "KeyValue", namespace = "http://www.netex.org.uk/netex")
    val keyValues: MutableList<KeyValue> = mutableListOf()
}
