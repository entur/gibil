package config

import SiriEtService
import handler.AvinorScheduleXmlHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import routes.api.AvinorApiHandler
import siri.SiriETMapper
import siri.SiriETPublisher
import siri.validator.XsdValidator
import java.time.Clock

@Configuration
class App {
    val avinorApi = AvinorApiHandler()
    val avxh = AvinorScheduleXmlHandler()
    val siriMapper = SiriETMapper()
    val siriPublisher = SiriETPublisher()
    val xsdValidator = XsdValidator()
    val clock = Clock.systemUTC()

    @Bean
    fun siriEtService(): SiriEtService {
        return SiriEtService(this)
    }
}