package org.gibil.subscription.helper

import org.gibil.subscription.model.SiriDataType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import uk.org.siri.siri21.EstimatedTimetableSubscriptionStructure
import uk.org.siri.siri21.SubscriptionQualifierStructure
import uk.org.siri.siri21.SubscriptionRefStructure
import uk.org.siri.siri21.SubscriptionRequest
import uk.org.siri.siri21.TerminateSubscriptionRequestStructure

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

        Assertions.assertEquals("sub-123", result)
    }

    @Test
    fun `Should return null when no subscription requests`() {
        val request = SubscriptionRequest()

        val result = SiriHelper.resolveSubscriptionId(request)

        Assertions.assertNull(result)
    }

    @Test
    fun `Should resolve SIRI data type as ESTIMATED_TIMETABLE`() {
        val request = SubscriptionRequest()
        val etRequest = EstimatedTimetableSubscriptionStructure()
        request.estimatedTimetableSubscriptionRequests.add(etRequest)

        val result = SiriHelper.resolveSiriDataType(request)

        Assertions.assertEquals(SiriDataType.ESTIMATED_TIMETABLE, result)
    }

    @Test
    fun `Should return null data type when no requests`() {
        val request = SubscriptionRequest()

        val result = SiriHelper.resolveSiriDataType(request)

        Assertions.assertNull(result)
    }

    @Test
    fun `Should create heartbeat notification with requestor ref`() {
        val requestorRef = "ENTUR_DEV"

        val result = SiriHelper.createHeartbeatNotification(requestorRef)

        Assertions.assertNotNull(result.heartbeatNotification)
        Assertions.assertTrue(result.heartbeatNotification.isStatus)
        Assertions.assertEquals(requestorRef, result.heartbeatNotification.producerRef.value)
        Assertions.assertNotNull(result.heartbeatNotification.serviceStartedTime)
        Assertions.assertNotNull(result.heartbeatNotification.requestTimestamp)
    }

    @Test
    fun `Should create subscription response with subscription ref`() {
        val subscriptionRef = "sub-456"

        val result = SiriHelper.createSubscriptionResponse(subscriptionRef)

        Assertions.assertNotNull(result.subscriptionResponse)
        Assertions.assertEquals("2.1", result.version)
        Assertions.assertEquals(subscriptionRef, result.subscriptionResponse.responderRef.value)
        Assertions.assertEquals(1, result.subscriptionResponse.responseStatuses.size)

        val status = result.subscriptionResponse.responseStatuses[0]
        Assertions.assertTrue(status.isStatus)
        Assertions.assertEquals(subscriptionRef, status.subscriptionRef.value)
    }

    @Test
    fun `Should create terminate subscription response`() {
        val terminateRequest = TerminateSubscriptionRequestStructure()
        val subRef = SubscriptionRefStructure()
        subRef.value = "sub-789"
        terminateRequest.subscriptionReves.add(subRef)

        val result = SiriHelper.createTerminateSubscriptionResponse(terminateRequest)

        Assertions.assertNotNull(result.terminateSubscriptionResponse)
        Assertions.assertEquals(1, result.terminateSubscriptionResponse.terminationResponseStatuses.size)

        val status = result.terminateSubscriptionResponse.terminationResponseStatuses[0]
        Assertions.assertTrue(status.isStatus)
        Assertions.assertEquals("sub-789", status.subscriptionRef.value)
        Assertions.assertNotNull(status.responseTimestamp)
    }
}