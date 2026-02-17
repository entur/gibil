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
    private val subscriptionManager: SubscriptionManager
) {
    private val logger = LoggerFactory.getLogger(AvinorPollingService::class.java)

    @Scheduled(fixedRate = 120000, initialDelay = 420000)
    fun pollAndPushUpdates() {
        logger.info("Starting Avinor data poll cycle")
        logger.info("Starting poll cycle. Cache size: {}", flightStateCache.getCacheSize())

        try {
            val allFlights = flightAggregationService.fetchAndMergeAllFlights()
            logger.info("Fetched {} flights", allFlights.size)
            flightStateCache.cleanCache(allFlights.keys)
            val changedFlights = flightStateCache.filterChanged(allFlights.values)

            if (changedFlights.isNotEmpty()) {
                logger.info("Detected {} changed flights out of {}", changedFlights.size, allFlights.size)

                val siri = siriETMapper.mapMergedFlightsToSiri(changedFlights)

                subscriptionManager.pushSiriToSubscribers(siri)
            } else {
                logger.debug("No flight changes detected")
            }
        } catch (e: Exception) {
            logger.error("Error during poll cycle: ${e.message}", e)
        }
    }
}