package subscription

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import service.FlightAggregationService
import siri.SiriETMapper

private val LOG = LoggerFactory.getLogger(AvinorPollingService::class.java)

/**
 * Service responsible for polling Avinor data, detecting changes, and pushing updates to subscribers.
 */
@Service
class AvinorPollingService(
    private val flightAggregationService: FlightAggregationService,
    private val flightStateCache: FlightStateCache,
    private val siriETMapper: SiriETMapper,
    private val subscriptionManager: SubscriptionManager
) {


    /**
     * //TODO: Consider putting initialDelay back to 10 seconds for production, 7 minutes is for testing and not do api calls during startup and resubscriptions.
     * Scheduled method that runs every 2 minutes after an initial delay of 7 minutes.
     * It fetches the latest flight data with the FlightAggregationService and compares it to the cached flight states in FlightStateCache.
     * If there are changes, it maps the changed flights to SIRI format using SiriETMapper and pushes the updates to subscribers via SubscriptionManager.
     */
    @Scheduled(fixedRate = 120000, initialDelay = 420000)
    fun pollAndPushUpdates() {
        LOG.info("Starting Avinor data poll cycle")
        LOG.info("Starting poll cycle. Cache size: {}", flightStateCache.getCacheSize())

        try {
            val (directFlights, multiLegFlights) = flightAggregationService.fetchAllFlights()
            LOG.info("Fetched {} direct flights, {} multi-leg flights", directFlights.size, multiLegFlights.size)
            // Cleaning cache before adding and filtering to not remove entries that are just added.
            flightStateCache.cleanCache(directFlights.keys)
            val changedFlights = flightStateCache.filterChanged(directFlights.values)

            if (changedFlights.isNotEmpty()) {
                LOG.info("Detected {} changed flights out of {}", changedFlights.size, directFlights.size)

                val siri = siriETMapper.mapAllFlightsToSiri(changedFlights, multiLegFlights)

                subscriptionManager.pushSiriToSubscribers(siri)
            } else {
                LOG.debug("No flight changes detected")
            }
        } catch (e: Exception) {
            LOG.error("Error during poll cycle: ${e.message}", e)
        }
    }
}