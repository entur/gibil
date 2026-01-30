package subscription

import uk.org.siri.siri21.RequestorRef
import java.time.Duration
import java.time.ZonedDateTime

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

    override fun toString(): String {
        return "Subscription[subscriptionType=$subscriptionType, subscriptionId='$subscriptionId']"
    }
}