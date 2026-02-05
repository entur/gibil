package org.gibil

import org.gibil.model.stopPlacesApi.Quay
import org.gibil.model.stopPlacesApi.StopPlaces
import org.springframework.stereotype.Component
import util.SharedJaxbContext
import java.io.StringReader

@Component
class StopPlaceMapper {

    fun unmarhsallStopPlaceXml(xmlData: String): StopPlaces {
        try {
            val unmarshaller = SharedJaxbContext.createUnmarshaller()
            return unmarshaller.unmarshal(StringReader(xmlData)) as StopPlaces

        } catch (e: Exception) {
            throw RuntimeException("Error parsing StopPlaces", e)
        }
    }

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

    private fun isolateIataCode(quay: Quay): String? {
        return quay.keyList?.keyValues
            ?.find { it.key == "imported-id" }
            ?.value
            ?.takeIf { it.startsWith("AVI:Quay:") }
            ?.removePrefix("AVI:Quay:")
    }
}