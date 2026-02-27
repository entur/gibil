package org.gibil.routes.avinor.airportname

import jakarta.annotation.PostConstruct
import model.airportNames.AirportNames
import org.gibil.service.ApiService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import util.SharedJaxbContext
import java.io.StringReader
import kotlin.text.uppercase

@Component
class AvinorAirportNamesApiHandler(
    private val apiService: ApiService,
    @Value("\${avinor.api.base-url-airport-names}") private val baseUrlAirportNames: String
) {

    private var airportIATASet = emptySet<String>()

    @PostConstruct
    internal fun init() {
        refreshAirportNameSet()
    }

    /**
     * Makes call to Avinors airportNames api, unmarshalls XML return into [AirportNames].
     * Makes set of IATAS in the [airportIATASet]
     */
    private fun refreshAirportNameSet() {
        val xml = apiService.apiCall(baseUrlAirportNames) ?: return
        val unmarshaller = SharedJaxbContext.createUnmarshaller()
        val airportNames = unmarshaller.unmarshal(StringReader(xml)) as AirportNames
        airportIATASet = airportNames.airportName
            .mapNotNull { it.code?.uppercase() }
            .toSet()
    }

    /**
     * Validates an airport code against the cached set of known Avinor airport codes.
     * The set is populated once at startup via [refreshAirportNameSet].
     * @param airportCode String, a three letter IATA airport code
     * @return true if the airport code exists in Avinor's system
     */
    fun airportCodeValidator(airportCode: String): Boolean {
        return airportCode.uppercase() in airportIATASet
    }

}