package org.gibil.service

import jakarta.annotation.PostConstruct
import org.gibil.handler.StopPlaceMapper
import org.gibil.util.TiamatImportPaths
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

    private var iataToQuayMap: Map<String, List<String>> = emptyMap()

    @PostConstruct
    internal fun init() {
        if (basePath == TiamatImportPaths.LOCAL_BASEPATH) {
            ZipUtil.downloadAndUnzip(stopPlaceDataUrl, basePath, apiService)
        }
        refreshQuayMapping()
    }

    fun refreshQuayMapping() {
        try {
            val file = File(basePath).listFiles { f -> f.extension.lowercase() == "xml" }?.firstOrNull()
                ?: throw RuntimeException("No XML file found in $basePath")
            val stopPlaces = mapper.parseStopPlaceFromFile(file)
            iataToQuayMap = mapper.makeIataToQuayMap(stopPlaces)
        } catch (e: Exception) {
            LOG.error("Failed to refresh quay mapping: {}", e.message)
        }
    }

    /**
     * Gets quayID of the first quay belonging to airport, must be reworked when gates get mapped to quays
     * @param iataCode String, used as key for map to fetch quayID belonging to airport.
     * @return String?, quayID
     */
     fun getQuayId(iataCode: String): String? {
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