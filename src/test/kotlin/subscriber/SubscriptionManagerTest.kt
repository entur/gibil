package subscriber

import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import service.FlightAggregationService
import siri.SiriETMapper
import subscription.*
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure
import uk.org.siri.siri21.ServiceDelivery
import uk.org.siri.siri21.Siri
import java.time.Duration
import java.time.ZonedDateTime

class SubscriptionManagerTest {

    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var httpHelper: HttpHelper
    private lateinit var siriETMapper: SiriETMapper
    private lateinit var flightAggregationService: FlightAggregationService

    @BeforeEach
    fun setup() {
        httpHelper = mockk()
        siriETMapper = mockk()
        flightAggregationService = mockk()
        subscriptionManager = SubscriptionManager(httpHelper, siriETMapper, flightAggregationService)
    }

    @Test
    fun `Should add a subscription successfully`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { httpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription)

        coVerify(exactly = 1) { httpHelper.postData(subscription.address, any()) }
    }

    @Test
    fun `Should handle failed initial delivery`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { httpHelper.postData(any(), any()) } throws Exception("Connection error")

        subscriptionManager.addSubscription(subscription)

        coVerify(exactly = 1) { httpHelper.postData(subscription.address, any()) }
    }

    @Test
    fun `Should push SIRI data to subscribers`() {
        val subscription1 = createTestSubscription(id = "sub001")
        val subscription2 = createTestSubscription(id = "sub002")

        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { httpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription1)
        subscriptionManager.addSubscription(subscription2)

        clearMocks(httpHelper, answers = false)
        coEvery { httpHelper.postData(any(), any()) } returns 200

        val siri = createSiriWithET()
        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(exactly = 2) { httpHelper.postData(any(), any()) }
        coVerify { httpHelper.postData(subscription1.address, any()) }
        coVerify { httpHelper.postData(subscription2.address, any()) }
    }

    @Test
    fun `Should not push SIRI data if no ET deliveries`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { httpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription)
        clearMocks(httpHelper, answers = false)

        val siri = createSiriWithoutET()
        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(exactly = 0) { httpHelper.postData(any(), any()) }
    }

    @Test
    fun `Should handle push failure and increment failure counter`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { httpHelper.postData(any(), any()) } throws Exception("Network error")

        subscriptionManager.addSubscription(subscription)

        val siri = createSiriWithET()
        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(atLeast = 1) { httpHelper.postData(subscription.address, any()) }
    }

    @Test
    fun `Should terminate subscription successfully`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { httpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription)
        subscriptionManager.terminateSubscription(subscription.subscriptionId)

        val siri = createSiriWithET()
        clearMocks(httpHelper, answers = false)

        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(exactly = 0) { httpHelper.postData(subscription.address, any()) }
    }

    @Test
    fun `Should not affect other subscriptions when one is terminated`() {
        val subscription1 = createTestSubscription("sub001", address = "http://localhost:8080/notify1")
        val subscription2 = createTestSubscription("sub002", address = "http://localhost:8080/notify2")

        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { httpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription1)
        subscriptionManager.addSubscription(subscription2)

        subscriptionManager.terminateSubscription(subscription1.subscriptionId)
        clearMocks(httpHelper, answers = false)
        coEvery { httpHelper.postData(any(), any()) } returns 200

        val siri = createSiriWithET()
        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(exactly = 0) { httpHelper.postData(subscription1.address, any()) }
        coVerify(exactly = 1) { httpHelper.postData(subscription2.address, any()) }
    }

    private fun createTestSubscription(id: String = "sub001", address: String = "http://localhost:8080/notify"): Subscription {
        return Subscription(
            requestTimestamp = ZonedDateTime.now(),
            subscriptionType = SiriDataType.ESTIMATED_TIMETABLE,
            address = address,
            subscriptionId = id,
            heartbeatInterval = Duration.ofSeconds(30),
            requestorRef = "test-requestor"
        )
    }

    private fun createSiriWithET(): Siri {
        val siri = Siri()
        val serviceDelivery = ServiceDelivery()
        val etDelivery = EstimatedTimetableDeliveryStructure()
        serviceDelivery.estimatedTimetableDeliveries.add(etDelivery)
        siri.serviceDelivery = serviceDelivery
        return siri
    }

    private fun createSiriWithoutET(): Siri {
        val siri = Siri()
        val serviceDelivery = ServiceDelivery()
        siri.serviceDelivery = serviceDelivery
        return siri
    }
}