import config.App

import java.time.Instant
import org.entur.siri.validator.SiriValidator
import org.springframework.stereotype.Service
import siri.validator.ValidationResult

private const val DEPATURE_CODE = "D"

/**
 * SiriEtService is a service responsible for calling AvinorApi and
 * unmarshall it before converting it to siri-et
 * This siri data is validated before being sent to endpoint
 * @param components application components and dependencies
 */
@Service
class SiriEtService(private val components: App) {

    /**
     * fetchAndConvert fetches XML data from the avinorScheduleApi,
     * unmharsalls it before converting it to siri-et
     * @param airportCode String, is used for fetching data from specific airports
     * @return siri-et formatted XML data
     */
    // Should be switched with iterating through all aiports
    fun fetchAndConvert(airportCode: String): String {
        val xmlData = components.avinorApi.avinorXmlFeedApiCall(
            airportCode,
            directionParam = DEPATURE_CODE,
            lastUpdateParam = Instant.now(components.clock),
            codeshareParam = true
        )

        val airport = components.avxh.unmarshallXmlToAirport(xmlData ?: "")
        val siri = components.siriMapper.mapToSiri(airport, airportCode)

        return components.siriPublisher.toXml(siri)
    }

    /**
     * validateXmlXsd uses existing validation logic to test siri XML against valid XSD
     * @param siriXml String, siri formatted XML data
     * @return validation result
     */
    fun validateXmlXsd(siriXml: String): ValidationResult {
        return components.xsdValidator.validateSirixml(siriXml, SiriValidator.Version.VERSION_2_1)
    }


}