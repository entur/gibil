package config

import SiriEtService
import handler.AvinorScheduleXmlHandler
import org.example.AirportService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import routes.api.AvinorApiHandler
import siri.SiriETMapper
import siri.SiriETPublisher
import siri.validator.XsdValidator
import java.time.Clock

@Configuration
class App (
    val avinorApi: AvinorApiHandler = AvinorApiHandler(),
    val avxh: AvinorScheduleXmlHandler = AvinorScheduleXmlHandler(),
    val siriMapper: SiriETMapper = SiriETMapper(),
    val siriPublisher: SiriETPublisher = SiriETPublisher(),
    val xsdValidator: XsdValidator = XsdValidator(),
    val clock: Clock = Clock.systemUTC()
) {
    @Bean
    fun siriEtService(): SiriEtService {
        return SiriEtService(this)
    }

    @Bean
    fun AirportService(): AirportService {
        return AirportService(this)
    }
}