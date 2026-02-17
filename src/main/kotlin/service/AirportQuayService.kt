package org.gibil.service

import jakarta.annotation.PostConstruct
import org.gibil.StopPlaceMapper
import org.gibil.routes.api.StopPlaceApiHandler
import org.springframework.stereotype.Service

@Service
open class AirportQuayService(private val handler: StopPlaceApiHandler, private val mapper: StopPlaceMapper) {

    private var iataToQuayMap: Map<String, List<String>> = emptyMap()


    @PostConstruct
    internal fun init() {
        refreshQuayMapping()
    }

    /**
     * Does StopPlace API call unmarshalls the returning XML data and maps quayIDs to their respective airport IATA codes
     * in the [iataToQuayMap].
     */
    fun refreshQuayMapping() {
        val xml = handler.fetchAirportStopPlaces() ?: return
        val stopPlaces = mapper.unmarhsallStopPlaceXml(xml)
        iataToQuayMap = mapper.makeIataToQuayMap(stopPlaces)
    }

    /**
     * Gets quayID of the first quay belonging to airport, must be reworked when gates get mapped to quays
     * @param iataCode String, used as key for map to fetch quayID belonging to airport.
     * @return String?, quayID
     */
    open fun getQuayId(iataCode: String): String? {
        return iataToQuayMap[iataCode]?.firstOrNull()
    }

    /**
     * Gets all quayIDs belonging to airport
     * @param iataCode String, used as key for map to fetch quaysIDs belonging to airport.
     * @return List<String>, list of quayIDs
     */
    fun getAllQuayIds(iataCode: String): List<String> {
        return iataToQuayMap[iataCode] ?: emptyList()
    }
}