package org.gibil.model.serviceJourney

import jakarta.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
class LineRefWrapper {

    @field:XmlAttribute(name = "ref")
    var ref: String? = ""
}