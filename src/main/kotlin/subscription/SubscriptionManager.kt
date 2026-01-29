package subscription

import org.entur.siri21.util.SiriXml
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository
import uk.org.siri.siri21.Siri
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Repository
class SubscriptionManager(
    @param:Autowired private val siriETRepository: SiriETRepository,
    @param:Autowired private val httpHelper: HttpHelper
) {
    private val subscriptions: MutableMap<String, Subscription> = HashMap()
    private val subscriptionFailCounter: MutableMap<String, Int> = HashMap()
    private val heartbeatExecutors: MutableMap<String, ScheduledExecutorService> = HashMap()

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

    fun addSubscription(subscription: Subscription) {
        LOG.info("Adding subscription: {}", subscription)
        subscriptions[subscription.subscriptionId] = subscription
        initHeartbeat(subscription)

        val initialDelivery: Siri? = when (subscription.subscriptionType) {
            SiriDataType.ESTIMATED_TIMETABLE -> SiriHelper.createSiriEtServiceDelivery(siriETRepository.all)
        }

        try {
            runBlocking { httpHelper.postData(subscription.address, SiriXml.toXml(initialDelivery)) }
        } catch (_: Exception) {
            LOG.warn("Initial delivery failed to address {}", subscription.address)
        }
        LOG.info("Added subscription: {}, now have {} subscriptions", subscription, subscriptions.size)
    }

    fun terminateSubscription(subscriptionId: String) {
        subscriptions.remove(subscriptionId)
        subscriptionFailCounter.remove(subscriptionId)
        stopHeartbeat(subscriptionId)
        LOG.info("Subscription terminated: {}", subscriptionId)
    }

    private fun stopHeartbeat(subscriptionId: String) {
        heartbeatExecutors[subscriptionId]?.shutdown()
        heartbeatExecutors.remove(subscriptionId)
    }

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

    private fun markFailed(subscription: Subscription) {
        val failedCounter = subscriptionFailCounter.getOrDefault(subscription.subscriptionId, 0)
        subscriptionFailCounter[subscription.subscriptionId] = failedCounter + 1
    }

    private fun hasFailed(subscription: Subscription): Boolean {
        val failedCounter = subscriptionFailCounter.getOrDefault(subscription.subscriptionId, 0)
        return failedCounter >= MAX_FAILED_COUNTER
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SubscriptionManager::class.java)
        private const val MAX_FAILED_COUNTER = 5
    }
}