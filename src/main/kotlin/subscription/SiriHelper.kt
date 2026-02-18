package subscription

import org.gibil.SIRI_VERSION_DELIVERY
import uk.org.siri.siri21.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

/**
 * Helper class for creating SIRI objects, such as service deliveries and heartbeat notifications,
 * centralizing it into one location.
 * Also contains utility methods for resolving subscription IDs
 * and data types from subscription requests.
 */
object SiriHelper {
    //Simple indication of when server was started
    private val serverStartTime: Instant = Instant.now()

    /**
     * Creates a SIRI Service Delivery object containing the provided EstimatedVehicleJourney elements.
     * This method constructs the necessary structure of the
     * SIRI Service Delivery, including EstimatedTimetableDelivery and EstimatedVersionFrame,
     * and populates it with the given EstimatedVehicleJourney elements.
     * @param elements A collection of EstimatedVehicleJourney elements to include in the Service Delivery.
     * @return A Siri object containing the constructed Service Delivery with the provided EstimatedVehicleJourney elements
     */
    fun createSiriEtServiceDelivery(elements: Collection<EstimatedVehicleJourney?>): Siri {
        val siri = createSiriServiceDelivery()
        siri.serviceDelivery
            .estimatedTimetableDeliveries
            .add(EstimatedTimetableDeliveryStructure())

        siri.serviceDelivery
            .estimatedTimetableDeliveries[0]
            .estimatedJourneyVersionFrames
            .add(EstimatedVersionFrameStructure())

        siri.serviceDelivery
            .estimatedTimetableDeliveries[0]
            .estimatedJourneyVersionFrames[0]
            .estimatedVehicleJourneies
            .addAll(elements)


        return siri
    }

    /**
     * Creates a basic SIRI Service Delivery object with the necessary structure,
     * but without any EstimatedVehicleJourney elements.
     * This method is used as a starting point for constructing Service Delivery objects,
     * allowing other methods to populate it with the appropriate data.
     * @return A Siri object containing an empty Service Delivery structure
     * ready to be populated with EstimatedVehicleJourney elements.
     */
    private fun createSiriServiceDelivery(): Siri {
        val siri = createSiriObject()
        val serviceDelivery = ServiceDelivery()

        siri.setServiceDelivery(serviceDelivery)
        return siri
    }

    /**
     * Creates a SIRI Heartbeat Notification object, which is used to indicate that the service is alive and functioning.
     * @param requestorRef An optional reference to the requestor, which can be included in the notification for identification purposes.
     * @return A Siri object containing the Heartbeat Notification with the provided requestor reference and current timestamps.
     */
    fun createHeartbeatNotification(requestorRef: String?): Siri {
        val siri = createSiriObject()
        val heartbeat = HeartbeatNotificationStructure()
        heartbeat.isStatus = true
        heartbeat.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()))
        heartbeat.setRequestTimestamp(ZonedDateTime.now())
        heartbeat.setProducerRef(createRequestorRef(requestorRef))
        siri.setHeartbeatNotification(heartbeat)
        return siri
    }

    /**
     * Creates a RequestorRef object with the provided reference value.
     * This method is used to create a RequestorRef that can be included in various
     * SIRI messages, such as subscription responses and heartbeat notifications, to identify the requestor.
     * @param requestorRef An optional string value to set as the reference in the RequestorRef object.
     * @return A RequestorRef object with the provided reference value set, or null if the input is null.
     */
    private fun createRequestorRef(requestorRef: String?): RequestorRef {
        val ref = RequestorRef()
        ref.setValue(requestorRef)
        return ref
    }

    /**
     * Creates a basic SIRI object with the version set to "2.1".
     * This method serves as a common starting point for creating various SIRI messages,
     * @return A Siri object with the version set to "2.1" and ready to be populated with specific message content.
     */
    private fun createSiriObject(): Siri {
        val siri = Siri()
        siri.setVersion(SIRI_VERSION_DELIVERY)
        return siri
    }

    /**
     * Resolves the subscription ID from a given SubscriptionRequest by checking for EstimatedTimetableSubscriptionRequests.
     * If such requests are present, it retrieves the subscription identifier from the first one and returns its value.
     * If no EstimatedTimetableSubscriptionRequests are found, it returns null.
     * @param subscriptionRequest The SubscriptionRequest from which to resolve the subscription ID.
     * @return The subscription ID as a string if found, or null if not found.
     */
    fun resolveSubscriptionId(subscriptionRequest: SubscriptionRequest): String? {
        if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isNotEmpty()) {
            val estimatedTimetableSubscriptionStructure =
                subscriptionRequest.getEstimatedTimetableSubscriptionRequests()[0]

            return estimatedTimetableSubscriptionStructure.getSubscriptionIdentifier().getValue()
        }
        return null
    }

    /**
     * Resolves the SIRI data type from a given SubscriptionRequest by checking for EstimatedTimetableSubscriptionRequests.
     * If such requests are present, it returns the corresponding SiriDataType (ESTIMATED_TIMETABLE).
     * If no EstimatedTimetableSubscriptionRequests are found, it returns null.
     * @param subscriptionRequest The SubscriptionRequest from which to resolve the SIRI data type.
     * @return The resolved SiriDataType if found, or null if not found.
     */
    fun resolveSiriDataType(subscriptionRequest: SubscriptionRequest): SiriDataType? {
        if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isNotEmpty()) {
            return SiriDataType.ESTIMATED_TIMETABLE
        }
        return null
    }

    /**
     * Creates a SIRI Subscription Response object in response to a subscription request,
     * including the necessary response status and timestamps.
     * This method constructs the SubscriptionResponseStructure,
     * sets the service start time, request message reference, responder reference, and response timestamp,
     * and then creates a ResponseStatus with the subscription reference and status.
     * The resulting Subscription Response is then encapsulated in a Siri object and returned.
     * @param subscriptionRef An optional string representing the subscription reference to include in the response status.
     * @return A Siri object containing the Subscription Response with the appropriate response status and timestamps.
     */
    fun createSubscriptionResponse(subscriptionRef: String?): Siri {
        val siri = createSiriObject()
        val response = SubscriptionResponseStructure()
        response.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()))
        response.setRequestMessageRef(createMessageRef())
        response.setResponderRef(createRequestorRef(subscriptionRef))
        response.setResponseTimestamp(ZonedDateTime.now())


        val responseStatus = ResponseStatus()
        responseStatus.setResponseTimestamp(ZonedDateTime.now())
        responseStatus.setRequestMessageRef(createMessageRef())
        responseStatus.setSubscriptionRef(createSubscriptionRef(subscriptionRef))

        responseStatus.isStatus = true

        response.getResponseStatuses().add(responseStatus)

        siri.setSubscriptionResponse(response)
        return siri
    }

    /**
     * Creates a SIRI Terminate Subscription Response object in response to a terminate subscription request,
     * including the necessary response status and timestamps.
     * This method constructs the TerminateSubscriptionResponseStructure, sets the response timestamp,
     * and creates a TerminationResponseStatusStructure with the subscription reference and status.
     * The resulting Terminate Subscription Response is then encapsulated in a Siri object and returned.
     * @param terminateSubscriptionRequest The TerminateSubscriptionRequestStructure containing the subscription reference to include in the response status.
     * @return A Siri object containing the Terminate Subscription Response with the appropriate response status and timestamps.
     */
    fun createTerminateSubscriptionResponse(terminateSubscriptionRequest: TerminateSubscriptionRequestStructure): Siri {
        val siri = createSiriObject()

        val response = TerminateSubscriptionResponseStructure()
        val status = TerminationResponseStatusStructure()
        status.setResponseTimestamp(ZonedDateTime.now())
        status.setSubscriptionRef(
            createSubscriptionRef(
                terminateSubscriptionRequest.getSubscriptionReves()[0].getValue()
            )
        )
        status.isStatus = true

        response.getTerminationResponseStatuses().add(status)
        siri.setTerminateSubscriptionResponse(response)
        return siri
    }

    /**
     * Creates a MessageRefStructure with a randomly generated UUID as its value.
     * This method is used to generate unique message references for SIRI messages,
     * ensuring that each message can be uniquely identified.
     * @return A MessageRefStructure with a randomly generated UUID as its value.
     */
    private fun createMessageRef(): MessageRefStructure {
        val ref = MessageRefStructure()
        ref.setValue(UUID.randomUUID().toString())
        return ref
    }

    /**
     * Creates a SubscriptionRefStructure with the provided subscription reference value.
     * This method is used to create a SubscriptionRef that can be included in response statuses,
     * allowing the recipient to identify which subscription the response is related to.
     * @param subscriptionRef An optional string value to set as the reference in the SubscriptionRef object.
     * @return A SubscriptionRefStructure with the provided reference value set, or null if the input is null.
     */
    private fun createSubscriptionRef(subscriptionRef: String?): SubscriptionRefStructure {
        val ref = SubscriptionRefStructure()
        ref.setValue(subscriptionRef)
        return ref
    }
}