package org.gibil.service

import jakarta.annotation.PostConstruct
import org.gibil.QuayCodes
import org.gibil.StopPlaceMapper
import org.gibil.routes.entur.StopPlaceApiHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private val LOG = LoggerFactory.getLogger(AirportQuayService::class.java)

@Service
class AirportQuayService(private val handler: StopPlaceApiHandler, private val mapper: StopPlaceMapper) {

    private var iataToQuayMap: Map<String, Map<String, String>> = emptyMap()


    @PostConstruct
    internal fun init() {
        refreshQuayMapping()
    }

    /**
     * Does StopPlace API call unmarshalls the returning XML data and maps quayIDs to their respective airport IATA codes
     * in the [iataToQuayMap].
     */
    fun refreshQuayMapping() {
        val xml = handler.fetchAirportStopPlaces().getOrElse { e ->
            LOG.error("Failed to refresh quay mapping: {}", e.message)
            if (iataToQuayMap.isEmpty()) {
                LOG.error("Quay map is empty, all quay resolutions will fail until successful refresh")
            }
            return
        }
        val stopPlaces = mapper.unmarshallStopPlaceXml(xml)
        val newMap = mapper.makeIataToQuayMap(stopPlaces)

        iataToQuayMap = buildMap{

            for ((iata, quayMap) in iataToQuayMap) {
                val oldDefault = quayMap[QuayCodes.DEFAULT_KEY] ?: continue
                put(iata, mapOf(QuayCodes.DEFAULT_KEY to oldDefault))
            }
            for ((iata, quayMap) in newMap) {
                put(iata, (get(iata) ?: emptyMap()) + quayMap)
            }
        }
        LOG.info("Quay map refreshed: {} airports", iataToQuayMap.size)
    }

    /**
     * Gets quayID of the first quay belonging to airport, must be reworked when gates get mapped to quays
     * @param iataCode String, used as key for map to fetch quayID belonging to airport.
     * @return String?, quayID
     */
     fun getQuayId(iataCode: String, gate: String? = null): String? {
         val quayMap = iataToQuayMap[iataCode] ?: return null
         return quayMap[gate] ?: quayMap[QuayCodes.DEFAULT_KEY]
     }
}