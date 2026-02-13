package subscription

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import service.FlightAggregationService
import siri.SiriETMapper

@Service
class AvinorPollingService(
    private val flightAggregationService: FlightAggregationService,
    private val flightStateCache: FlightStateCache,
    private val siriETMapper: SiriETMapper,
    private val siriETRepository: SiriETRepository,
    private val subscriptionManager: SubscriptionManager
) {
    private val logger = LoggerFactory.getLogger(AvinorPollingService::class.java)

    @Scheduled(fixedRate = 120000, initialDelay = 5000)
    fun pollAndPushUpdates() {
        logger.info("Starting Avinor data poll cycle")

        try {
            val allFlights = flightAggregationService.fetchAndMergeAllFlights()
            val changedFlights = flightStateCache.filterChanged(allFlights.values)

            if (changedFlights.isNotEmpty()) {
                logger.info("Detected ${changedFlights.size} changed flights")

                val siri = siriETMapper.mapMergedFlightsToSiri(changedFlights)

                // Update repository and push to subscribers
                siri.serviceDelivery?.estimatedTimetableDeliveries?.firstOrNull()
                    ?.estimatedJourneyVersionFrames?.firstOrNull()
                    ?.estimatedVehicleJourneies?.forEach { evj ->
                        siriETRepository.siriData[evj.datedVehicleJourneyRef?.value ?: return@forEach] = evj
                    }

                subscriptionManager.pushSiriToSubscribers(siri)
            } else {
                logger.debug("No flight changes detected")
            }
        } catch (e: Exception) {
            logger.error("Error during poll cycle: ${e.message}", e)
        }
    }
}