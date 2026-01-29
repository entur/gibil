package subscription

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.org.siri.siri21.EstimatedVehicleJourney
import uk.org.siri.siri21.Siri
import java.time.Duration

@RestController
class SiriEndpoint(
    @Autowired private val subscriptionManager: SubscriptionManager,
    @Autowired private val siriETRepository: SiriETRepository
) {

    @PostMapping(value = ["/subscribe"], produces = ["application/xml"], consumes = ["application/xml"])
    fun handleSubscriptionRequest(@RequestBody siriRequest: Siri): Siri {
        val subscriptionRequest = siriRequest.subscriptionRequest

        var address = subscriptionRequest.consumerAddress
        if (address == null) {
            address = subscriptionRequest.address
        }
        val requestorRef = subscriptionRequest.requestorRef

        // Convert javax.xml.datatype.Duration to java.time.Duration
        val xmlDuration = subscriptionRequest.subscriptionContext.heartbeatInterval
        val heartbeatInterval: Duration = Duration.parse(xmlDuration.toString())

        // Handle nullable SiriDataType
        val siriDataType = SiriHelper.resolveSiriDataType(siriRequest.subscriptionRequest)
            ?: throw IllegalArgumentException("Unable to resolve SIRI data type")

        // Handle nullable subscriptionId
        val subscriptionId = SiriHelper.resolveSubscriptionId(siriRequest.subscriptionRequest)
            ?: throw IllegalArgumentException("Unable to resolve subscription ID")

        val subscription = Subscription(
            siriRequest.subscriptionRequest.requestTimestamp,
            siriDataType,
            address,
            subscriptionId,
            heartbeatInterval,
            requestorRef
        )
        subscriptionManager.addSubscription(subscription)
        return SiriHelper.createSubscriptionResponse(subscription.subscriptionId)
    }

    @PostMapping(value = ["/unsubscribe"], produces = ["application/xml"], consumes = ["application/xml"])
    fun handleTerminateSubscriptionRequest(@RequestBody siriRequest: Siri): Siri {
        val terminateSubscriptionRequest = siriRequest.terminateSubscriptionRequest
        subscriptionManager.terminateSubscription(terminateSubscriptionRequest.subscriptionReves[0].value)
        return SiriHelper.createTerminateSubscriptionResponse(terminateSubscriptionRequest)
    }

    @PostMapping(value = ["/service"], produces = ["application/xml"], consumes = ["application/xml"])
    fun handleServiceRequest(@RequestBody siriRequest: Siri): Siri {
        val serviceRequest = siriRequest.serviceRequest
        serviceRequest.estimatedTimetableRequests

        // Use non-nullable collection
        val siriEtElements: Collection<EstimatedVehicleJourney> = siriETRepository.all
        return SiriHelper.createSiriEtServiceDelivery(siriEtElements)
    }
}