package subscription

import org.entur.siri21.util.SiriXml
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository
import uk.org.siri.siri21.Siri
import kotlinx.coroutines.runBlocking
import service.FlightAggregationService
import siri.SiriETMapper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val LOG: Logger = LoggerFactory.getLogger(SubscriptionManager::class.java)

/**
 * Manages SIRI subscriptions, including adding, terminating, and pushing updates to subscribers.
 */
@Repository
class SubscriptionManager(
    @param:Autowired private val httpHelper: HttpHelper,
    @param:Autowired private val siriETMapper: SiriETMapper,
    @param:Autowired private val flightAggregationService: FlightAggregationService,
    @param:Autowired private val flightStateCache: FlightStateCache
) {
    private val subscriptions: MutableMap<String, Subscription> = HashMap()
    private val subscriptionFailCounter: MutableMap<String, Int> = HashMap()
    private val heartbeatExecutors: MutableMap<String, ScheduledExecutorService> = HashMap()

    companion object {
        private const val MAX_FAILED_COUNTER = 5
    }

    /**
     * Pushes SIRI data to all subscribers that are subscribed to the relevant data type.
     * If a push fails, it increments the failure counter for that subscription and logs a warning.
     * If the failure counter exceeds a predefined threshold, the subscription is terminated.
     * @param siri The SIRI data to be pushed to subscribers.
     */
    fun pushSiriToSubscribers(siri: Siri) {
        LOG.info("Pushing data to {} subscribers.", subscriptions.size)
        if (siri.serviceDelivery.estimatedTimetableDeliveries.isNotEmpty()) {
            for (subscription in subscriptions.values) {
                if (subscription.subscriptionType == SiriDataType.ESTIMATED_TIMETABLE) {
                    try {
                        runBlocking { httpHelper.postData(subscription.address, SiriXml.toXml(siri)) }
                    } catch (e: Exception) {
                        val subscriptionId = subscription.subscriptionId
                        val failures = subscriptionFailCounter.getOrDefault(subscriptionId, 0) + 1
                        subscriptionFailCounter[subscriptionId] = failures
                        LOG.warn(
                            "Failed to push SIRI data to subscription {} at address {} (failure count: {})",
                            subscriptionId,
                            subscription.address,
                            failures,
                            e
                        )
                    }
                }
            }
        }
    }

    /**
     * Adds a new subscription and initializes a heartbeat for it. It also sends an initial delivery of SIRI data to the subscriber.
     * If the initial delivery fails, it logs a warning but does not terminate the subscription immediately.
     * @param subscription The subscription to be added. Based on subscription.kt model.
     */
    fun addSubscription(subscription: Subscription) {
        LOG.info("Adding subscription: {}", subscription)
        subscriptions[subscription.subscriptionId] = subscription
        initHeartbeat(subscription)

        val initialDelivery: Siri? = when (subscription.subscriptionType) {
            SiriDataType.ESTIMATED_TIMETABLE -> {
                val initialData = flightAggregationService.fetchAndMergeAllFlights()
                flightStateCache.populateCache(initialData.values)
                siriETMapper.mapMergedFlightsToSiri(initialData.values)
            }
        }

        try {
            runBlocking { httpHelper.postData(subscription.address, SiriXml.toXml(initialDelivery)) }
        } catch (_: Exception) {
            LOG.warn("Initial delivery failed to address {}", subscription.address)
        }
        LOG.info("Added subscription: {}, now have {} subscriptions", subscription, subscriptions.size)
    }

    /**
     * Terminates a subscription by its ID, removing it from the active subscriptions and stopping its heartbeat.
     * It also resets the failure counter for that subscription.
     * @param subscriptionId The ID of the subscription to be terminated as a string.
     */
    fun terminateSubscription(subscriptionId: String) {
        subscriptions.remove(subscriptionId)
        subscriptionFailCounter.remove(subscriptionId)
        stopHeartbeat(subscriptionId)
        LOG.info("Subscription terminated: {}", subscriptionId)
    }

    /**
     * Stops the heartbeat for a given subscription ID by shutting down the associated executor service
     * and removing it from the map.
     * @param subscriptionId The ID of the subscription for which to stop the heartbeat as a string.
     */
    private fun stopHeartbeat(subscriptionId: String) {
        heartbeatExecutors[subscriptionId]?.shutdown()
        heartbeatExecutors.remove(subscriptionId)
    }

    /**
     * Initializes a heartbeat for a subscription by scheduling a task that periodically
     * sends a heartbeat message to the subscriber's address.
     * If the heartbeat fails, it increments the failure counter for that subscription and logs a warning.
     * If the failure counter exceeds a predefined threshold, the subscription is terminated.
     * @param subscription The subscription for which to initialize the heartbeat. Based on subscription.kt model
     */
    private fun initHeartbeat(subscription: Subscription) {
        val heartbeatExecutorService = Executors.newSingleThreadScheduledExecutor()
        heartbeatExecutorService.scheduleAtFixedRate(
            {
                try {
                    if (hasFailed(subscription)) {
                        LOG.warn("Subscription has failed {} times, removing.", subscriptionFailCounter[subscription.subscriptionId])
                        terminateSubscription(subscription.subscriptionId)
                    } else {
                        LOG.info("Posting heartbeat to {}", subscription)
                        val responseCode = runBlocking { httpHelper.postHeartbeat(subscription.address, subscription.subscriptionId) }
                        if (responseCode != 200) {
                            markFailed(subscription)
                        }
                        LOG.info("Posted heartbeat to {}", subscription)
                    }
                } catch (e: Exception) {
                    markFailed(subscription)
                    LOG.warn("Post heartbeat to {} failed with {}.", subscription, e.message)
                }
            },
            subscription.heartbeatInterval.seconds,
            subscription.heartbeatInterval.seconds,
            TimeUnit.SECONDS
        )

        LOG.info("Adding heartbeat for subscription {} every {} s", subscription, subscription.heartbeatInterval.seconds)
        heartbeatExecutors[subscription.subscriptionId]?.shutdown()
        heartbeatExecutors.remove(subscription.subscriptionId)
        heartbeatExecutors[subscription.subscriptionId] = heartbeatExecutorService
    }

    /**
     * Increments the failure counter for a given subscription.
     * This method is called when a push or heartbeat attempt fails.
     * @param subscription The subscription for which to mark a failure. Based on subscription.kt model.
     */
    private fun markFailed(subscription: Subscription) {
        val failedCounter = subscriptionFailCounter.getOrDefault(subscription.subscriptionId, 0)
        subscriptionFailCounter[subscription.subscriptionId] = failedCounter + 1
    }

    /**
     * Checks if a subscription has exceeded the maximum allowed failures.
     * If the failure counter for the subscription is greater than or equal to the predefined threshold,
     * this method returns true, indicating that the subscription should be considered failed and potentially terminated.
     * @param subscription The subscription to check for failure status. Based on subscription.kt model.
     * @return true if the subscription has failed too many times, false otherwise.
     */
    private fun hasFailed(subscription: Subscription): Boolean {
        val failedCounter = subscriptionFailCounter.getOrDefault(subscription.subscriptionId, 0)
        return failedCounter >= MAX_FAILED_COUNTER
    }
}