package subscription

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import subscription.SiriHelper.createSiriEtServiceDelivery
import uk.org.siri.siri21.EstimatedVehicleJourney
import uk.org.siri.siri21.Siri

@Repository
class SiriETRepository {
    protected val siriData: MutableMap<String, EstimatedVehicleJourney> = HashMap()

    val all: MutableCollection<EstimatedVehicleJourney>
        get() = siriData.values

    fun add(element: EstimatedVehicleJourney, subscriptionManager: SubscriptionManager) {
        LOG.info("Adding SIRI-data: {}", element)
        siriData[createKey(element)] = element
        subscriptionManager.pushSiriToSubscribers(createServiceDelivery())
    }

    protected fun createServiceDelivery(): Siri {
        return createSiriEtServiceDelivery(all)
    }

    protected fun createKey(element: EstimatedVehicleJourney): String {
        return element.datedVehicleJourneyRef.value
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SiriETRepository::class.java)
    }
}