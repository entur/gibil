package subscription

import uk.org.siri.siri21.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

object SiriHelper {
    //Simple indication of when server was started
    private val serverStartTime: Instant = Instant.now()

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

    private fun createSiriServiceDelivery(): Siri {
        val siri = createSiriObject()
        val serviceDelivery = ServiceDelivery()

        siri.setServiceDelivery(serviceDelivery)
        return siri
    }

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

    private fun createRequestorRef(requestorRef: String?): RequestorRef {
        val ref = RequestorRef()
        ref.setValue(requestorRef)
        return ref
    }

    private fun createSiriObject(): Siri {
        val siri = Siri()
        siri.setVersion("2.1")
        return siri
    }

    fun resolveSubscriptionId(subscriptionRequest: SubscriptionRequest): String? {
        if (!subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isEmpty()) {
            val estimatedTimetableSubscriptionStructure =
                subscriptionRequest.getEstimatedTimetableSubscriptionRequests()[0]

            return estimatedTimetableSubscriptionStructure.getSubscriptionIdentifier().getValue()
        }
        return null
    }

    fun resolveSiriDataType(subscriptionRequest: SubscriptionRequest): SiriDataType? {
        if (!subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isEmpty()) {
            return SiriDataType.ESTIMATED_TIMETABLE
        }
        return null
    }

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

    private fun createMessageRef(): MessageRefStructure {
        val ref = MessageRefStructure()
        ref.setValue(UUID.randomUUID().toString())
        return ref
    }

    private fun createSubscriptionRef(subscriptionRef: String?): SubscriptionRefStructure {
        val ref = SubscriptionRefStructure()
        ref.setValue(subscriptionRef)
        return ref
    }
}