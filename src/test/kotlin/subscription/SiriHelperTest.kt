package subscription

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import uk.org.siri.siri21.*

class SiriHelperTest {

    @Test
    fun `Should resolve subscription ID from ET request`() {
        val request = SubscriptionRequest()
        val etRequest = EstimatedTimetableSubscriptionStructure()
        val subId = SubscriptionQualifierStructure()
        subId.value = "sub-123"
        etRequest.subscriptionIdentifier = subId
        request.estimatedTimetableSubscriptionRequests.add(etRequest)

        val result = SiriHelper.resolveSubscriptionId(request)

        assertEquals("sub-123", result)
    }

    @Test
    fun `Should return null when no subscription requests`() {
        val request = SubscriptionRequest()

        val result = SiriHelper.resolveSubscriptionId(request)

        assertNull(result)
    }

    @Test
    fun `Should resolve SIRI data type as ESTIMATED_TIMETABLE`() {
        val request = SubscriptionRequest()
        val etRequest = EstimatedTimetableSubscriptionStructure()
        request.estimatedTimetableSubscriptionRequests.add(etRequest)

        val result = SiriHelper.resolveSiriDataType(request)

        assertEquals(SiriDataType.ESTIMATED_TIMETABLE, result)
    }

    @Test
    fun `Should return null data type when no requests`() {
        val request = SubscriptionRequest()

        val result = SiriHelper.resolveSiriDataType(request)

        assertNull(result)
    }

    @Test
    fun `Should create heartbeat notification with requestor ref`() {
        val requestorRef = "ENTUR_DEV"

        val result = SiriHelper.createHeartbeatNotification(requestorRef)

        assertNotNull(result.heartbeatNotification)
        assertTrue(result.heartbeatNotification.isStatus)
        assertEquals(requestorRef, result.heartbeatNotification.producerRef.value)
        assertNotNull(result.heartbeatNotification.serviceStartedTime)
        assertNotNull(result.heartbeatNotification.requestTimestamp)
    }

    @Test
    fun `Should create subscription response with subscription ref`() {
        val subscriptionRef = "sub-456"

        val result = SiriHelper.createSubscriptionResponse(subscriptionRef)

        assertNotNull(result.subscriptionResponse)
        assertEquals("2.1", result.version)
        assertEquals(subscriptionRef, result.subscriptionResponse.responderRef.value)
        assertEquals(1, result.subscriptionResponse.responseStatuses.size)

        val status = result.subscriptionResponse.responseStatuses[0]
        assertTrue(status.isStatus)
        assertEquals(subscriptionRef, status.subscriptionRef.value)
    }

    @Test
    fun `Should create terminate subscription response`() {
        val terminateRequest = TerminateSubscriptionRequestStructure()
        val subRef = SubscriptionRefStructure()
        subRef.value = "sub-789"
        terminateRequest.subscriptionReves.add(subRef)

        val result = SiriHelper.createTerminateSubscriptionResponse(terminateRequest)

        assertNotNull(result.terminateSubscriptionResponse)
        assertEquals(1, result.terminateSubscriptionResponse.terminationResponseStatuses.size)

        val status = result.terminateSubscriptionResponse.terminationResponseStatuses[0]
        assertTrue(status.isStatus)
        assertEquals("sub-789", status.subscriptionRef.value)
        assertNotNull(status.responseTimestamp)
    }
}