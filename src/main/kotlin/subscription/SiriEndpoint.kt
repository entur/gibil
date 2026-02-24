package subscription

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.org.siri.siri21.Siri
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration

/**
 * REST controller that handles incoming SIRI subscription and service requests.
 * It processes the requests, manages subscriptions, and generates appropriate responses.
 * The controller has three endpoints, subscription, unsubscription and service, each handling different types of SIRI requests.
 */
@RestController
class SiriEndpoint(
    @Autowired private val subscriptionManager: SubscriptionManager,
    @Value("\${siri.allowed-subscriber-hosts}") private val allowedSubscriberHosts: List<String>
) {

    /**
     * Handles incoming subscription requests. It extracts necessary information from the request,
     * creates a new Subscription object, and adds it to the SubscriptionManager.
     * It also generates a subscription response to be sent back to the requester.
     * @param siriRequest The incoming SIRI subscription request, expected to be in XML format and deserialized into a Siri object.
     * @return A Siri object representing the subscription response, which will be serialized back to XML and sent to the requester.
     */
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

        validateSubscriberAddress(address)

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

    private fun validateSubscriberAddress(address: String?) {
        val uri = try {
            URI(address ?: throw IllegalArgumentException("Subscriber address is missing"))
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("Invalid subscriber address: $address", e)
        }
        val host = uri.host ?: throw IllegalArgumentException("Subscriber address has no host: $address")
        if (host !in allowedSubscriberHosts) {
            throw IllegalArgumentException("Subscriber host not in allowlist: $host")
        }
    }

    /**
     * Handles incoming unsubscription requests. It extracts the subscription ID from the request,
     * terminates the corresponding subscription in the SubscriptionManager, and generates an unsubscription response.
     * @param siriRequest The incoming SIRI unsubscription request, expected to be in XML format and deserialized into a Siri object.
     * @return A Siri object representing the unsubscription response, which will be serialized back to XML and sent to the requester.
     */
    @PostMapping(value = ["/unsubscribe"], produces = ["application/xml"], consumes = ["application/xml"])
    fun handleTerminateSubscriptionRequest(@RequestBody siriRequest: Siri): Siri {
        val terminateSubscriptionRequest = siriRequest.terminateSubscriptionRequest
        subscriptionManager.terminateSubscription(terminateSubscriptionRequest.subscriptionReves[0].value)
        return SiriHelper.createTerminateSubscriptionResponse(terminateSubscriptionRequest)
    }
}

    /**
     * Handles incoming service requests. It processes the request, retrieves the relevant Estimated Vehicle Journey (ET)
     * data from the SiriETRepository, and generates a service delivery response containing the ET data.
     * @param siriRequest The incoming SIRI service request, expected to be in XML format and deserialized into a Siri object.
     * @return A Siri object representing the service delivery response, which will be serialized back to XML and sent to the requester.

    @Autowired private val siriETRepository: SiriETRepository

    @PostMapping(value = ["/service"], produces = ["application/xml"], consumes = ["application/xml"])
    fun handleServiceRequest(@RequestBody siriRequest: Siri): Siri {
        val serviceRequest = siriRequest.serviceRequest
        serviceRequest.estimatedTimetableRequests

        // Use non-nullable collection
        val siriEtElements: Collection<EstimatedVehicleJourney> = siriETRepository.all
        return SiriHelper.createSiriEtServiceDelivery(siriEtElements)
    }
}
        */