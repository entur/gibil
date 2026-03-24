package service

import io.mockk.mockk
import org.gibil.service.ApiService
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class FindServiceJourneyServiceTest {
    val service = FindServiceJourneyService(mockk<ApiService>(), "src/test/resources/extimeData").also { it.init() }

    //correct information
    val exampleFlightSasSVG = listOf("2026-05-05T06:00:00Z", "SK4011")
    val exampleFlightNorwegian = listOf("2026-03-02T17:25:00Z", "DY628")
    val exampleLineRefSas = listOf("OSL", "SVG")
    val exampleLineRefNorwegian = listOf("OSL", "BGO")

    //wrong flight code
    val exampleNanFlightcode = listOf("2026-03-05T08:00:00Z", "NotReal")
    val exampleLineNanFlight = listOf("OSL", "SVG")

    // Wrong date, valid flight code and lineref — tests that date matching actually gates the result
    val exampleWrongDate = listOf("2026-01-01T06:00:00Z", "SK4011")

    // Wrong lineRef, valid flight code and date — tests that lineref matching works correctly
    val exampleWrongLineRef = listOf("OSL", "LAX")

    // midnight edgecase, a fake flight i've manually made, since none existed around midnight
    val exampleMidnight = listOf("2026-05-24T22:30:00Z", "DY9999")
    val exampleLineRefMidnight = listOf("OSL", "TRD")

    @Test
    fun `FindServiceJourney should find service journey match to example flights`() {
        val foundMatch1 = service.matchServiceJourney(exampleFlightSasSVG[0], exampleFlightSasSVG[1], exampleLineRefSas)
        val foundMatch2 = service.matchServiceJourney(exampleFlightNorwegian[0], exampleFlightNorwegian[1], exampleLineRefNorwegian)

        Assertions.assertTrue { "SK4011-01-358551288" in foundMatch1.serviceJourneyId }
        Assertions.assertTrue { "DY628-01-523288933" in foundMatch2.serviceJourneyId }
    }

    @Test
    fun `FindServiceJourney should throw exception when not finding a matching servicejourney`() {
        //we should not find a match for the flightcode that does not exist in the servicejourneys
        Assertions.assertThrows(ServiceJourneyNotFoundException::class.java) {
            service.matchServiceJourney(exampleNanFlightcode[0], exampleNanFlightcode[1], exampleLineNanFlight)
        }
    }

    @Test
    fun `FindServiceJourney should throw when date does not match even if flight code and lineref exists`() {
        Assertions.assertThrows(ServiceJourneyNotFoundException::class.java) {
            service.matchServiceJourney(exampleWrongDate[0], exampleWrongDate[1], exampleLineRefSas)
        }
    }

    @Test
    fun `FindServiceJourney should throw when airports from avinor dosent match lineref info`() {
        Assertions.assertThrows(ServiceJourneyNotFoundException::class.java) {
            service.matchServiceJourney(exampleFlightSasSVG[0], exampleFlightSasSVG[1], exampleWrongLineRef)
        }
    }

    @Test
    fun `FindServiceJourney should find ID when flight is around midnight`() {
        val foundMatch = service.matchServiceJourney(exampleMidnight[0], exampleMidnight[1], exampleLineRefMidnight)

        Assertions.assertTrue { "DY9999-01-123456789" in foundMatch.serviceJourneyId }
    }

    @Test
    fun `MutableServiceJourneyList should match ServiceJourneyList after resetMutableServiceJourneyList()`() {
        service.resetMutableServiceJourneyMap()

        Assertions.assertTrue { service.mutableServiceJourneyMap == service.serviceJourneyList }
    }

    @Test
    fun `MutableServiceJourneyList should remove a servicejourney after resetMutableServiceJourneyList()`() {
        service.resetMutableServiceJourneyMap()

        val originalSize = service.serviceJourneyList.size

        //run a few matches
        service.matchServiceJourney(exampleMidnight[0], exampleMidnight[1], exampleLineRefMidnight)
        service.matchServiceJourney(exampleFlightSasSVG[0], exampleFlightSasSVG[1], exampleLineRefSas)
        service.matchServiceJourney(exampleFlightNorwegian[0], exampleFlightNorwegian[1], exampleLineRefNorwegian)

        //since three matches are run, the expected result is three less servicejourneys
        val expectedSize = originalSize-3
        val actualSize = service.mutableServiceJourneyMap.size

        Assertions.assertTrue { actualSize == expectedSize }
    }
}