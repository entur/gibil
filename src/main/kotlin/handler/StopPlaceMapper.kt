package org.gibil.handler

import org.gibil.model.stopPlacesApi.Quay
import org.gibil.model.stopPlacesApi.StopPlaces
import org.springframework.stereotype.Component
import util.SharedJaxbContext
import java.io.File
import javax.xml.stream.XMLInputFactory

@Component
class StopPlaceMapper {

    //TODO - put in try catch?
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
        return StopPlaces()
    }

    /**
     * Maps quays belonging to specific airport.
     * Airports IATA code is key and quayIDs are values in the list.
     * @param stopPlaces StopPlaces
     * @return Map<String, List<String>>
     */
    fun makeIataToQuayMap(stopPlaces: StopPlaces): Map<String, List<String>> {
        return stopPlaces.stopPlace
            .flatMap { sp -> sp.quays?.quay ?: emptyList() }
            .mapNotNull { quay ->
                isolateIataCode(quay)?.let { iataCode ->
                    iataCode to quay.id
                }
            }
            .groupBy({it.first}, {it.second})
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