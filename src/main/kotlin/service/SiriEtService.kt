package service
import routes.api.AvinorXmlFeedParams

import handler.AvinorScheduleXmlHandler
import java.time.Clock
import org.entur.siri.validator.SiriValidator
import org.springframework.stereotype.Service
import routes.api.AvinorApiHandler
import siri.SiriETMapper
import siri.SiriETPublisher
import siri.validator.ValidationResult
import siri.validator.XsdValidator

private const val DEPATURE_CODE = "" //Empty to show both Departures("D") and Arrivals("A")

/**
 * SiriEtService is a service responsible for calling AvinorApi and
 * unmarshall it before converting it to siri-et
 * This siri data is validated before being sent to endpoint
 */
@Service
class SiriEtService(
    private val avinorApi: AvinorApiHandler,
    private val avxh: AvinorScheduleXmlHandler,
    private val siriMapper: SiriETMapper,
    private val siriPublisher: SiriETPublisher,
    private val xsdValidator: XsdValidator,
    private val clock: Clock
) {

    /**
     * fetchAndConvert fetches XML data from the avinorScheduleApi,
     * unmharsalls it before converting it to siri-et
     * @param airportCode String, is used for fetching data from specific airports
     * @return siri-et formatted XML data
     */
    // Should be switched with iterating through all aiports
    fun fetchAndConvert(airportCode: String): String {
        val url = avinorApi.avinorXmlFeedUrlBuilder(
            AvinorXmlFeedParams(
                airportCode = airportCode,
                direction = DEPATURE_CODE
            )
        )
        val xmlData = avinorApi.apiCall(url)

        val airport = avxh.unmarshallXmlToAirport(xmlData ?: "")
        val siri = siriMapper.mapToSiri(airport, airportCode)

        return siriPublisher.toXml(siri)
    }

    /**
     * validateXmlXsd uses existing validation logic to test siri XML against valid XSD
     * @param siriXml String, siri formatted XML data
     * @return validation result
     */
    fun validateXmlXsd(siriXml: String): ValidationResult {
        return xsdValidator.validateSirixml(siriXml, SiriValidator.Version.VERSION_2_1)
    }
}