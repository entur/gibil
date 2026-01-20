package routes.api

import SiriEtService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class Endpoint(private val siriEtService: SiriEtService) {

    @GetMapping("/siri", produces = [MediaType.APPLICATION_XML_VALUE])
    fun siriEtEndpoint(@RequestParam(defaultValue = "OSL") airport: String): String {
        return siriEtService.fetchAndConvert(airport)
    }
}