package service

import model.AvinorXmlFeedParams
import handler.AvinorScheduleXmlHandler
import org.gibil.service.ApiService
import org.springframework.stereotype.Service
import routes.api.AvinorApiHandler
import siri.SiriETMapper
import siri.SiriETPublisher

private const val DEPARTURE_CODE = "" //Empty to show both Departures("D") and Arrivals("A")

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
    private val apiService: ApiService
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
                direction = DEPARTURE_CODE
            )
        )
        val xmlData = apiService.apiCall(url)

        val airport = avxh.unmarshallXmlToAirport(xmlData ?: "")
        val siri = siriMapper.mapToSiri(airport, airportCode)

        return siriPublisher.toXml(siri)
    }
}