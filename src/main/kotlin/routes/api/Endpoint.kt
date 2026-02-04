package routes.api

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import service.SiriEtService

@RestController
class Endpoint(
    private val siriEtService: SiriEtService,
    private val avinorApiHandler: AvinorApiHandler
) {

    @GetMapping("/siri", produces = [MediaType.APPLICATION_XML_VALUE])
    fun siriEtEndpoint(@RequestParam(defaultValue = "OSL") airport: String): String {
        return siriEtService.fetchAndConvert(airport)
    }

    /**
     * Debug endpoint that returns the raw XML response from the Avinor API.
     */
    @GetMapping("/avinor", produces = [MediaType.APPLICATION_XML_VALUE])
    fun rawAvinorEndpoint(
        @RequestParam(defaultValue = "OSL") airport: String,
    ): String {
        val url = avinorApiHandler.avinorXmlFeedUrlBuilder(
            AvinorXmlFeedParams(airportCode = airport)
        )
        return avinorApiHandler.apiCall(url) ?: "Error: No response from Avinor API"
    }
}