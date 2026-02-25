package subscription

import io.mockk.*
import org.gibil.subscription.helper.SubscriptionHttpHelper
import org.gibil.subscription.model.SiriDataType
import org.gibil.subscription.model.Subscription
import org.gibil.subscription.repository.FlightStateCache
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import service.FlightAggregationService
import siri.SiriETMapper
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure
import uk.org.siri.siri21.ServiceDelivery
import uk.org.siri.siri21.Siri
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class SubscriptionManagerTest {

    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var SubscriptionHttpHelper: SubscriptionHttpHelper
    private lateinit var siriETMapper: SiriETMapper
    private lateinit var flightAggregationService: FlightAggregationService
    private lateinit var flightStateCache: FlightStateCache

    @BeforeEach
    fun setup() {
        SubscriptionHttpHelper = mockk()
        siriETMapper = mockk()
        flightAggregationService = mockk()
        flightStateCache = mockk(relaxed = true)
        subscriptionManager = SubscriptionManager(SubscriptionHttpHelper, siriETMapper, flightAggregationService, flightStateCache)
    }

    @Test
    fun `Should add a subscription successfully`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription)

        coVerify(exactly = 1) { SubscriptionHttpHelper.postData(subscription.address, any()) }
    }

    @Test
    fun `Should handle failed initial delivery`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } throws Exception("Connection error")

        subscriptionManager.addSubscription(subscription)

        coVerify(exactly = 1) { SubscriptionHttpHelper.postData(subscription.address, any()) }
    }

    @Test
    fun `Should push SIRI data to subscribers`() {
        val subscription1 = createTestSubscription(id = "sub001")
        val subscription2 = createTestSubscription(id = "sub002")

        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription1)
        subscriptionManager.addSubscription(subscription2)

        clearMocks(SubscriptionHttpHelper, answers = false)
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200

        val siri = createSiriWithET()
        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(exactly = 2) { SubscriptionHttpHelper.postData(any(), any()) }
        coVerify { SubscriptionHttpHelper.postData(subscription1.address, any()) }
        coVerify { SubscriptionHttpHelper.postData(subscription2.address, any()) }
    }

    @Test
    fun `Should not push SIRI data if no ET deliveries`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription)
        clearMocks(SubscriptionHttpHelper, answers = false)

        val siri = createSiriWithoutET()
        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(exactly = 0) { SubscriptionHttpHelper.postData(any(), any()) }
    }

    @Test
    fun `Should handle push failure and increment failure counter`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } throws Exception("Network error")

        subscriptionManager.addSubscription(subscription)

        val siri = createSiriWithET()
        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(atLeast = 1) { SubscriptionHttpHelper.postData(subscription.address, any()) }
    }

    @Test
    fun `Should terminate subscription successfully`() {
        val subscription = createTestSubscription()
        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription)
        subscriptionManager.terminateSubscription(subscription.subscriptionId)

        val siri = createSiriWithET()
        clearMocks(SubscriptionHttpHelper, answers = false)

        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(exactly = 0) { SubscriptionHttpHelper.postData(subscription.address, any()) }
    }

    @Test
    fun `Should not affect other subscriptions when one is terminated`() {
        val subscription1 = createTestSubscription("sub001", address = "http://localhost:8080/notify1")
        val subscription2 = createTestSubscription("sub002", address = "http://localhost:8080/notify2")

        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription1)
        subscriptionManager.addSubscription(subscription2)

        subscriptionManager.terminateSubscription(subscription1.subscriptionId)
        clearMocks(SubscriptionHttpHelper, answers = false)
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200

        val siri = createSiriWithET()
        subscriptionManager.pushSiriToSubscribers(siri)

        coVerify(exactly = 0) { SubscriptionHttpHelper.postData(subscription1.address, any()) }
        coVerify(exactly = 1) { SubscriptionHttpHelper.postData(subscription2.address, any()) }
    }

    @Test
    fun `Should terminate subscription after max failed heartbeats`() {
        val subscription = createTestSubscription()
        val runnableSlot = slot<Runnable>()
        val mockExecutor = mockk<ScheduledExecutorService>()

        // Capture the runnable scheduled by initHeartbeat
        every { mockExecutor.scheduleAtFixedRate(capture(runnableSlot), any(), any(), any()) } returns mockk()
        every { mockExecutor.shutdown() } just Runs

        mockkStatic(Executors::class)
        every { Executors.newSingleThreadScheduledExecutor() } returns mockExecutor

        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200

        subscriptionManager.addSubscription(subscription)

        // Simulate 5 failed heartbeats to trip the threshold
        coEvery { SubscriptionHttpHelper.postHeartbeat(any(), any()) } returns 500
        repeat(5) { runnableSlot.captured.run() }

        // 6th run should now see hasFailed = true and terminate
        runnableSlot.captured.run()

        coVerify(atLeast = 5) { SubscriptionHttpHelper.postHeartbeat(subscription.address, any()) }

        unmockkStatic(Executors::class)
    }

    @Test
    fun `Should mark failed when heartbeat throws exception`() {
        val subscription = createTestSubscription()
        val runnableSlot = slot<Runnable>()
        val mockExecutor = mockk<ScheduledExecutorService>()

        every { mockExecutor.scheduleAtFixedRate(capture(runnableSlot), any(), any(), any()) } returns mockk()
        every { mockExecutor.shutdown() } just Runs

        mockkStatic(Executors::class)
        every { Executors.newSingleThreadScheduledExecutor() } returns mockExecutor

        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200
        coEvery { SubscriptionHttpHelper.postHeartbeat(any(), any()) } throws Exception("Connection refused")

        subscriptionManager.addSubscription(subscription)

        // Should not throw — exception is caught internally
        runnableSlot.captured.run()

        coVerify(exactly = 1) { SubscriptionHttpHelper.postHeartbeat(subscription.address, any()) }

        unmockkStatic(Executors::class)
    }

    @Test
    fun `Should mark failed when heartbeat returns non-200`() {
        val subscription = createTestSubscription()
        val runnableSlot = slot<Runnable>()
        val mockExecutor = mockk<ScheduledExecutorService>()

        every { mockExecutor.scheduleAtFixedRate(capture(runnableSlot), any(), any(), any()) } returns mockk()
        every { mockExecutor.shutdown() } just Runs

        mockkStatic(Executors::class)
        every { Executors.newSingleThreadScheduledExecutor() } returns mockExecutor

        every { flightAggregationService.fetchAndMergeAllFlights() } returns emptyMap()
        every { siriETMapper.mapMergedFlightsToSiri(any()) } returns createSiriWithET()
        coEvery { SubscriptionHttpHelper.postData(any(), any()) } returns 200
        coEvery { SubscriptionHttpHelper.postHeartbeat(any(), any()) } returns 503

        subscriptionManager.addSubscription(subscription)
        runnableSlot.captured.run()

        coVerify(exactly = 1) { SubscriptionHttpHelper.postHeartbeat(subscription.address, any()) }

        unmockkStatic(Executors::class)
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