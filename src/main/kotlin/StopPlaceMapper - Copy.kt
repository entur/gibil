package org.gibil

import org.gibil.model.stopPlacesApi.Quay
import org.gibil.model.stopPlacesApi.StopPlaces
import org.springframework.stereotype.Component
import util.SharedJaxbContext
import java.io.StringReader

@Component
class StopPlaceMapper {

    /**
     * Unmarshalls StopPlace XML data into JAXB classes
     * @param xmlData String, XML StopPlace data fetched from EnTurs stopPlaces API
     * @return [StopPlaces] class containing needed API data
     */
    fun unmarshallStopPlaceXml(xmlData: String): StopPlaces {
        try {
            val unmarshaller = SharedJaxbContext.createUnmarshaller()
            return unmarshaller.unmarshal(StringReader(xmlData)) as StopPlaces

        } catch (e: Exception) {
            throw RuntimeException("Error parsing StopPlaces", e)
        }
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