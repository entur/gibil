package org.gibil.service

import jakarta.annotation.PostConstruct
import org.gibil.handler.StopPlaceMapper
import org.gibil.util.TiamatImportPaths
import org.gibil.util.QuayCodes
import util.ZipUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

private val LOG = LoggerFactory.getLogger(AirportQuayService::class.java)

@Service
class AirportQuayService(
    private val mapper: StopPlaceMapper,
    private val apiService: ApiService,
    @Value("\${stop.place.data.url}") private val stopPlaceDataUrl: String,
    @Value("\${org.gibil.stopPlace.data-file:#{null}}") private val configuredPath: String?
) {
    private val basePath = configuredPath
        ?: if (File(TiamatImportPaths.CLOUD_BASEPATH).exists()) TiamatImportPaths.CLOUD_BASEPATH
        else TiamatImportPaths.LOCAL_BASEPATH

    private var iataToQuayMap: Map<String, Map<String, String>> = emptyMap()

    @PostConstruct
    internal fun init() {
        if (basePath == TiamatImportPaths.LOCAL_BASEPATH) {
            ZipUtil.downloadAndUnzip(stopPlaceDataUrl, basePath, apiService)
        }
        refreshQuayMapping()
    }

    /**
     * Refreshes the in-memory IATA-to-quay mapping by parsing the tiamat export file in [basePath].
     * Parses the first (and only) XML file in the directory, parses it using [StopPlaceMapper], and replaces
     * the current [iataToQuayMap]. If no file is found or parsing fails, the existing map is
     * preserved and the error is logged.
     */
    fun refreshQuayMapping() {
        try {
            val file = File(basePath).listFiles { f -> f.extension.lowercase() == "xml" }?.firstOrNull()
                ?: throw RuntimeException("No XML file found in $basePath")
            val stopPlaces = mapper.parseStopPlaceFromFile(file)
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
        } catch (e: Exception) {
            LOG.error("Failed to refresh quay mapping: {}", e.message)
        }
    }

    /**
     * Gets quayID of the first quay belonging to airport, must be reworked when gates get mapped to quays
     * @param iataCode String, used as key for map to fetch quayID belonging to airport.
     * @return String?, quayID
     */
    //TODO NO LONGER ACCEPTABLE
    fun getQuayId(iataCode: String, gate: String? = null): String? {
        val quayMap = iataToQuayMap[iataCode] ?: return null
        return quayMap[gate] ?: quayMap[QuayCodes.DEFAULT_KEY]
    }
}