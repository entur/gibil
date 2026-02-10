package org.gibil

import org.junit.jupiter.api.Assertions
import service.FilterExtimeAndFindServiceJourney
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import service.ServiceJourneyNotFoundException
import kotlin.test.Test
import kotlin.test.assertNotNull

public class FilterExtimeAndFindServiceJourneyTest {
    val service = FilterExtimeAndFindServiceJourney(true)
    //correct information
    val exampleFlightSasSVG = listOf("2026-03-05T08:00:00Z", "SK4011")
    val exampleFlightNorwegian = listOf("2026-03-02T18:25:00Z", "DY628")
    val exampleFlightSasTRD = listOf("2026-05-10T17:55:00Z", "SK349")
    val exampleLines = setOf("AVI:Line:DY_OSL-BGO", "AVI:Line:SK_OSL-SVG", "AVI:Line:SK_OSL-TRD")

    //wrong information
    val exampleNanFlightcode = listOf("2026-03-05T08:00:00Z", "Flyet mitt er ikke gult")


    //everything is correct and we get the expected result
    @Test
    fun `filterExtimeAndWriteResults should filter extime and find servicejourney and find flights in servicejourneys`() {
        //filter extime input data to only include the lines we need
        service.filterExtimeAndWriteResults(exampleLines)

        val foundMatch = service.matchServiceJourney(exampleFlightSasSVG[0], exampleFlightSasSVG[1])
        val foundMatch2 = service.matchServiceJourney(exampleFlightSasTRD[0], exampleFlightSasTRD[1])
        val foundMatch3 = service.matchServiceJourney(exampleFlightNorwegian[0], exampleFlightNorwegian[1])

        assertTrue { "SK4011-02-358551288" in foundMatch }
        assertTrue { "SK349-03-465081146" in foundMatch2 }
        assertTrue { "DY628-01-523288933" in foundMatch3 }

    }

    @Test
    fun `filterExtimeAndWriteResults should filter extime data and find servicejourney and only find some flights`() {
        //filter extime input data to only include the lines we need
        service.filterExtimeAndWriteResults(exampleLines)

        val foundMatch = service.matchServiceJourney(exampleFlightSasSVG[0], exampleFlightSasSVG[1])
        val foundMatch3 = service.matchServiceJourney(exampleFlightNorwegian[0], exampleFlightNorwegian[1])

        assertTrue { "SK4011-02-358551288" in foundMatch }
        assertTrue { "DY628-01-523288933" in foundMatch3 }

        //we should not find a match for the flightcode that does not exist in the servicejourneys
        Assertions.assertThrows(ServiceJourneyNotFoundException::class.java) {
            service.matchServiceJourney(exampleNanFlightcode[0], exampleNanFlightcode[1])
        }
    }

    @Test
    fun `filterExtimeAndWriteResults `() {

    }

}
