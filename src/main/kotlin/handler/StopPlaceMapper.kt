package org.gibil.handler

import org.gibil.model.stopPlaces.Quay
import org.gibil.model.stopPlaces.StopPlaces
import org.gibil.util.QuayCodes
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import util.SharedJaxbContext
import java.io.File
import javax.xml.stream.XMLInputFactory

private val LOG = LoggerFactory.getLogger(StopPlaceMapper::class.java)

@Component
class StopPlaceMapper {

    /**
     * Parses a NeTEx XML file and extracts the [StopPlaces] element using StAX streaming.
     * Seeks to the first `<stopPlaces>` element in the file and unmarshals it with JAXB,
     * allowing large files to be handled without loading the full document into memory.
     * @param file the NeTEx XML file to parse.
     * @return [StopPlaces] object containing all airport stop places and their quay data.
     * @throws RuntimeException if no `<stopPlaces>` element is found in the file.
     */
    fun parseStopPlaceFromFile(file: File): StopPlaces {

        file.inputStream().use { inputStream ->
            val reader = XMLInputFactory.newInstance().createXMLStreamReader(inputStream)

            while (reader.hasNext()) {
                reader.next()

                if (reader.isStartElement && reader.localName == "stopPlaces") {
                    return SharedJaxbContext.createUnmarshaller().unmarshal(reader) as StopPlaces
                }
            }
            reader.close()
        }
        LOG.error("No <stopPlaces> found in file: {}", file.name)
        throw RuntimeException("No <stopPlaces> found in ${file.name}")
    }

    /**
     * Builds a two-level map from [StopPlaces] data for quay resolution.
     * The outer key is the airport IATA code, derived from the quay whose
     * `imported-id` matches `AVI:Quay:{IATA}`. Stop places without such a quay are skipped.
     *
     * The inner map contains:
     * - [QuayCodes.DEFAULT_KEY] -> the airport-level quay ID (always present)
     * - gate code (e.g. `"B16"`) -> gate-level quay ID (if present in the stop place register)
     *
     * @param stopPlaces the parsed stop place data.
     * @return map of IATA code -> (gate code or [QuayCodes.DEFAULT_KEY]) -> quay ID.
     */
    fun makeIataToQuayMap(stopPlaces: StopPlaces): Map<String, Map<String, String>> {
        return stopPlaces.stopPlace.mapNotNull { sp ->
            val quays = sp.quays?.quay ?: return@mapNotNull null
            val defaultQuay = quays.firstOrNull { isolateIataCode(it) != null } ?: return@mapNotNull null
            val iataCode = isolateIataCode(defaultQuay)!!

            val quayMap = buildMap {
                put(QuayCodes.DEFAULT_KEY, defaultQuay.id)
                quays.filter { it !== defaultQuay }.forEach { quay ->
                    val gateCode = quay.publicCode
                    if (!gateCode.isNullOrBlank()) put(gateCode, quay.id)
                }
            }
            iataCode to quayMap
        }.toMap()
    }

    /**
     * Isolates IATA and removes unwanted prefix to allow for IATA code to be used as key.
     * @param quay Quay, class containing unmarshalled StopPlace data
     * @return String?, returns IATA code stripped of unwanted prefix
     */
    private fun isolateIataCode(quay: Quay): String? {
        return quay.keyList?.keyValues
            ?.find { it.key == "imported-id" }
            ?.value
            ?.takeIf { it.startsWith("AVI:Quay:") }
            ?.removePrefix("AVI:Quay:")
    }
}