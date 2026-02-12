package subscription

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import subscription.SiriHelper.createSiriEtServiceDelivery
import uk.org.siri.siri21.EstimatedVehicleJourney
import uk.org.siri.siri21.Siri

/**
 * Repository for managing Estimated Vehicle Journey data in SIRI format.
 * It allows adding new ET data and retrieving all stored ET data.
 * When new ET data is added, it pushes the updated SIRI data to all subscribers via the SubscriptionManager.
 * The repository uses a simple in-memory map to store the ET data, keyed by a unique identifier derived from the ET data itself.
 * The createKey method generates a unique key for each ET data entry based on the datedVehicleJourneyRef value,
 * ensuring that each entry can be uniquely identified and retrieved.
 */
@Repository
class SiriETRepository {
    protected val siriData: MutableMap<String, EstimatedVehicleJourney> = HashMap()

    val all: MutableCollection<EstimatedVehicleJourney>
        get() = siriData.values

    /**
     * Adds a new Estimated Vehicle Journey to the repository and pushed to subscribers.
     * The method logs the addition and updates the internal map with the new ET data.
     * It then creates new Siri data using createServiceDelivery and pushes to subscribers.
     * @param element The EstimatedVehicleJourney to be added to the repository.
     * @param subscriptionManager The SubscriptionManager used to push updates to subscribers after adding new ET data.
     */
    fun add(element: EstimatedVehicleJourney, subscriptionManager: SubscriptionManager) {
        LOG.info("Adding SIRI-data: {}", element)
        siriData[createKey(element)] = element
        subscriptionManager.pushSiriToSubscribers(createServiceDelivery())
    }

    /**
     * Creates a new Siri object containing the current Estimated Vehicle Journey data from the repository.
     * This method uses the createSiriEtServiceDelivery helper function to generate a Siri object.
     * @return A Siri object containing the current ET data from the repository, ready to be pushed to subscribers.
     */
    protected fun createServiceDelivery(): Siri {
        return createSiriEtServiceDelivery(all)
    }

    /**
     * Generates a unique key for an Estimated Vehicle Journey based on its datedVehicleJourneyRef value.
     * This method ensures that each ET entry in the repository can be uniquely identified and retrieved.
     * @param element The EstimatedVehicleJourney for which to create a unique key.
     * @return A unique string key derived from the datedVehicleJourneyRef value of the ET element,
     * used for storing and retrieving the ET data in the repository's internal map.
     */
    protected fun createKey(element: EstimatedVehicleJourney): String {
        return element.datedVehicleJourneyRef.value
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SiriETRepository::class.java)
    }
}
