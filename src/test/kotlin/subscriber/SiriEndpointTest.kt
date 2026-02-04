package subscriber

import io.mockk.*
import jakarta.xml.bind.JAXBContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import subscription.*
import uk.org.siri.siri21.*
import java.io.StringReader

class SiriEndpointTest {
    private lateinit var endpoint: SiriEndpoint
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var siriETRepository: SiriETRepository
    private val jaxbContext = JAXBContext.newInstance(Siri::class.java)

    @BeforeEach
    fun setup(){
        subscriptionManager = mockk(relaxed = false)
        siriETRepository = mockk()
        endpoint = SiriEndpoint(subscriptionManager, siriETRepository)
    }

    @Test
    fun `Should handle subscription request and add subscription`(){
        val siriRequest = createSubscriptionRequest()
        every { subscriptionManager.addSubscription(any()) } just Runs

        val response = endpoint.handleSubscriptionRequest(siriRequest)

        verify(exactly = 1) {
            subscriptionManager.addSubscription(match { subscription ->
                subscription.subscriptionId == "SUBSCRIPTION_ID" &&
                        subscription.address == "https://SERVER:PORT/full/path/to/consumer/endpoint" &&
                        subscription.requestorRef == "ENTUR_DEV"
            })
        }
        assertNotNull(response.subscriptionResponse)
        assertEquals("2.1", response.version)
    }

    @Test
    fun `Should use consumerAddress when address is null`(){
        val siriRequest = createSubscriptionRequestWithConsumerAddress()
        every { subscriptionManager.addSubscription(any()) } just Runs

        val response = endpoint.handleSubscriptionRequest(siriRequest)

        verify(exactly = 1) {
            subscriptionManager.addSubscription(match { subscription ->
                subscription.address == "https://CONSUMER:PORT/endpoint"
            })
        }
        assertNotNull(response.subscriptionResponse)
    }

    @Test
    fun `Should throw exception when subscription ID is null`(){
        val siriRequest = createInvalidSubscriptionRequest()

        assertThrows<NullPointerException> {
            endpoint.handleSubscriptionRequest(siriRequest)
        }

        verify(exactly = 0) { subscriptionManager.addSubscription(any()) }
    }

    @Test
    fun `Should throw exception when SIRI data type is null`(){
        val siriRequest = createSubscriptionRequestWithoutDataType()

        val exception = assertThrows<IllegalArgumentException> {
            endpoint.handleSubscriptionRequest(siriRequest)
        }

        assertEquals("Unable to resolve SIRI data type", exception.message)
        verify(exactly = 0) { subscriptionManager.addSubscription(any()) }
    }

    @Test
    fun `Should handle terminate subscription request`(){
        val siriRequest = createTerminateSubscriptionRequest()
        every { subscriptionManager.terminateSubscription("SUBSCRIPTION_ID") } just Runs

        val response = endpoint.handleTerminateSubscriptionRequest(siriRequest)

        verify(exactly = 1) { subscriptionManager.terminateSubscription("SUBSCRIPTION_ID") }
        assertNotNull(response.terminateSubscriptionResponse)
        assertEquals(1, response.terminateSubscriptionResponse.terminationResponseStatuses.size)
    }

    @Test
    fun `Should handle service request and return ET data`(){
        val siriRequest = createServiceRequest()
        val mockJourney = mockk<EstimatedVehicleJourney>()
        every { siriETRepository.all } returns mutableListOf(mockJourney)

        val response = endpoint.handleServiceRequest(siriRequest)

        assertNotNull(response.serviceDelivery)
        assertNotNull(response.serviceDelivery.estimatedTimetableDeliveries)
        assertEquals(1, response.serviceDelivery.estimatedTimetableDeliveries.size)
        verify(exactly = 1) { siriETRepository.all }
    }

    @Test
    fun `Should handle service request with empty repository`(){
        val siriRequest = createServiceRequest()
        every { siriETRepository.all } returns mutableListOf()

        val response = endpoint.handleServiceRequest(siriRequest)

        assertNotNull(response.serviceDelivery)
        assertNotNull(response.serviceDelivery.estimatedTimetableDeliveries)
        verify(exactly = 1) { siriETRepository.all }
    }

    private fun createSubscriptionRequest(): Siri {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri">
    <SubscriptionRequest>
        <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
        <Address>https://SERVER:PORT/full/path/to/consumer/endpoint</Address>
        <RequestorRef>ENTUR_DEV</RequestorRef>
        <MessageIdentifier>ad2c0501-dd99-468a-a1bc-91ac8fbd7543</MessageIdentifier>
        <SubscriptionContext>
            <HeartbeatInterval>PT60S</HeartbeatInterval>
        </SubscriptionContext>
        <EstimatedTimetableSubscriptionRequest>
            <SubscriberRef>ENTUR_DEV</SubscriberRef>
            <SubscriptionIdentifier>SUBSCRIPTION_ID</SubscriptionIdentifier>
            <InitialTerminationTime>2020-12-03T13:25:00+01:00</InitialTerminationTime>
            <EstimatedTimetableRequest version="2.0">
                <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
                <PreviewInterval>PT10H</PreviewInterval>
            </EstimatedTimetableRequest>
            <ChangeBeforeUpdates>PT10S</ChangeBeforeUpdates>
        </EstimatedTimetableSubscriptionRequest>
    </SubscriptionRequest>
</Siri>""".trimIndent()

        val unmarshaller = jaxbContext.createUnmarshaller()
        return unmarshaller.unmarshal(StringReader(xml)) as Siri
    }

    private fun createSubscriptionRequestWithConsumerAddress(): Siri {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri">
    <SubscriptionRequest>
        <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
        <ConsumerAddress>https://CONSUMER:PORT/endpoint</ConsumerAddress>
        <RequestorRef>ENTUR_DEV</RequestorRef>
        <MessageIdentifier>ad2c0501-dd99-468a-a1bc-91ac8fbd7543</MessageIdentifier>
        <SubscriptionContext>
            <HeartbeatInterval>PT60S</HeartbeatInterval>
        </SubscriptionContext>
        <EstimatedTimetableSubscriptionRequest>
            <SubscriberRef>ENTUR_DEV</SubscriberRef>
            <SubscriptionIdentifier>SUBSCRIPTION_ID</SubscriptionIdentifier>
            <InitialTerminationTime>2020-12-03T13:25:00+01:00</InitialTerminationTime>
            <EstimatedTimetableRequest version="2.0">
                <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
                <PreviewInterval>PT10H</PreviewInterval>
            </EstimatedTimetableRequest>
        </EstimatedTimetableSubscriptionRequest>
    </SubscriptionRequest>
</Siri>""".trimIndent()

        val unmarshaller = jaxbContext.createUnmarshaller()
        return unmarshaller.unmarshal(StringReader(xml)) as Siri
    }

    private fun createInvalidSubscriptionRequest(): Siri {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri">
    <SubscriptionRequest>
        <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
        <Address>https://SERVER:PORT/endpoint</Address>
        <RequestorRef>ENTUR_DEV</RequestorRef>
        <SubscriptionContext>
            <HeartbeatInterval>PT60S</HeartbeatInterval>
        </SubscriptionContext>
        <EstimatedTimetableSubscriptionRequest>
            <SubscriberRef>ENTUR_DEV</SubscriberRef>
            <!-- No SubscriptionIdentifier here -->
            <EstimatedTimetableRequest version="2.0">
                <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
            </EstimatedTimetableRequest>
        </EstimatedTimetableSubscriptionRequest>
    </SubscriptionRequest>
</Siri>""".trimIndent()

        val unmarshaller = jaxbContext.createUnmarshaller()
        return unmarshaller.unmarshal(StringReader(xml)) as Siri
    }

    private fun createSubscriptionRequestWithoutDataType(): Siri {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri">
    <SubscriptionRequest>
        <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
        <Address>https://SERVER:PORT/endpoint</Address>
        <RequestorRef>ENTUR_DEV</RequestorRef>
        <SubscriptionContext>
            <HeartbeatInterval>PT60S</HeartbeatInterval>
        </SubscriptionContext>
    </SubscriptionRequest>
</Siri>""".trimIndent()

        val unmarshaller = jaxbContext.createUnmarshaller()
        return unmarshaller.unmarshal(StringReader(xml)) as Siri
    }

    private fun createTerminateSubscriptionRequest(): Siri {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri">
    <TerminateSubscriptionRequest>
        <RequestTimestamp>2019-12-03T14:00:00+01:00</RequestTimestamp>
        <RequestorRef>ENTUR_DEV</RequestorRef>
        <MessageIdentifier>terminate-001</MessageIdentifier>
        <SubscriptionRef>SUBSCRIPTION_ID</SubscriptionRef>
    </TerminateSubscriptionRequest>
</Siri>""".trimIndent()

        val unmarshaller = jaxbContext.createUnmarshaller()
        return unmarshaller.unmarshal(StringReader(xml)) as Siri
    }

    private fun createServiceRequest(): Siri {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri">
    <ServiceRequest>
        <RequestTimestamp>2019-12-03T13:00:00+01:00</RequestTimestamp>
        <RequestorRef>ENTUR_DEV</RequestorRef>
        <EstimatedTimetableRequest>
            <RequestTimestamp>2019-12-03T13:00:00+01:00</RequestTimestamp>
            <PreviewInterval>PT10H</PreviewInterval>
        </EstimatedTimetableRequest>
    </ServiceRequest>
</Siri>""".trimIndent()

        val unmarshaller = jaxbContext.createUnmarshaller()
        return unmarshaller.unmarshal(StringReader(xml)) as Siri
    }
}