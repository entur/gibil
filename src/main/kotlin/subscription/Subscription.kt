package subscription

import uk.org.siri.siri21.RequestorRef
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Represents a subscription to SIRI data.
 * Contains all information about the subscription, such as type, address and heartbeat interval.
 */
data class Subscription(
    val requestTimestamp: ZonedDateTime,
    val subscriptionType: SiriDataType,
    val address: String,
    val subscriptionId: String,
    val heartbeatInterval: Duration,
    val requestorRef: String
) {
    constructor(
        requestTimestamp: ZonedDateTime,
        subscriptionType: SiriDataType,
        address: String,
        subscriptionId: String,
        heartbeatInterval: Duration,
        requestorRef: RequestorRef
    ) : this(
        requestTimestamp,
        subscriptionType,
        address,
        subscriptionId,
        heartbeatInterval,
        requestorRef.value
    )

    /**
     * Overrides the default toString method to provide a more concise representation of the subscription,
     * only including the subscription type and ID.
     */
    override fun toString(): String {
        return "Subscription[subscriptionType=$subscriptionType, subscriptionId='$subscriptionId']"
    }
}