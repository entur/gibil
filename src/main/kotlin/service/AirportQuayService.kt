package org.gibil.service

import jakarta.annotation.PostConstruct
import org.gibil.StopPlaceMapper
import org.gibil.routes.api.StopPlaceApiHandler
import org.springframework.stereotype.Service

@Service
class AirportQuayService(private val handler: StopPlaceApiHandler, private val mapper: StopPlaceMapper) {

    private var iataToQuayMap: Map<String, List<String>> = emptyMap()

    @PostConstruct
    fun init() {
        refreshQuayMapping()
    }

    fun refreshQuayMapping() {
        val xml = handler.fetchAirportStopPlaces() ?: return
        val stopPlaces = mapper.unmarhsallStopPlaceXml(xml)
        iataToQuayMap = mapper.makeIataToQuayMap(stopPlaces)
    }

    fun getQuayId(iataCode: String): String? {
        return iataToQuayMap[iataCode]?.firstOrNull()
    }

    fun getAllQuayIds(iataCode: String): List<String> {
        return iataToQuayMap[iataCode] ?: emptyList()
    }
}