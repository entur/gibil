package subscriber

import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import subscription.*
import uk.org.siri.siri21.DatedVehicleJourneyRef
import uk.org.siri.siri21.EstimatedVehicleJourney
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SiriETRepositoryTest {

    private lateinit var repository: SiriETRepository
    private lateinit var subscriptionManager: SubscriptionManager

    @BeforeEach
    fun setup() {
        subscriptionManager = mockk(relaxed = true)
        repository = SiriETRepository()
    }

    @Test
    fun `Should add element and push to subscribers`() {
        val journey = mockk<EstimatedVehicleJourney>()
        val journeyRef = mockk<DatedVehicleJourneyRef>()
        every { journey.datedVehicleJourneyRef } returns journeyRef
        every { journeyRef.value } returns "journey-001"

        repository.add(journey, subscriptionManager)

        assertTrue(repository.all.contains(journey))
        verify(exactly = 1) { subscriptionManager.pushSiriToSubscribers(any()) }
    }

    @Test
    fun `Should replace element with same key`(){
        val journey1 = mockk<EstimatedVehicleJourney>()
        val journey2 = mockk<EstimatedVehicleJourney>()
        val journeyRef = mockk<DatedVehicleJourneyRef>()

        every { journey1.datedVehicleJourneyRef } returns journeyRef
        every { journey2.datedVehicleJourneyRef } returns journeyRef
        every { journeyRef.value } returns "journey-001"

        repository.add(journey1, subscriptionManager)
        repository.add(journey2, subscriptionManager)

        assertEquals(1, repository.all.size)
    }

}