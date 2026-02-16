package org.gibil

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import service.FindServiceJourney
import service.ServiceJourneyNotFoundException
import java.io.File
import kotlin.test.Test

public class FindServiceJourneyTest {
    val service = FindServiceJourney(true)
    //correct information
    val exampleFlightSasSVG = listOf("2026-05-05T06:00:00Z", "SK4011")
    val exampleFlightNorwegian = listOf("2026-03-02T17:25:00Z", "DY628")
    val exampleFlightSasTRD = listOf("2026-05-10T18:55:00Z", "SK349")
    val exampleLines = setOf("AVI:Line:DY_OSL-BGO", "AVI:Line:SK_OSL-SVG", "AVI:Line:SK_OSL-TRD")

    //wrong information
    val exampleNanFlightcode = listOf("2026-03-05T08:00:00Z", "Flyet mitt er ikke gult")

    val exampleNanDate = "imorgen klokka 12"

    @Test
    fun `FindServiceJourney should find service journey match to example flights`() {
        val foundMatch = service.matchServiceJourney(exampleFlightSasSVG[0], exampleFlightSasSVG[1])
        val foundMatch3 = service.matchServiceJourney(exampleFlightNorwegian[0], exampleFlightNorwegian[1])

        println(foundMatch)
        assertTrue { "SK4011-01-358551288" in foundMatch }
        assertTrue { "DY628-01-523288933" in foundMatch3 }
    }

    @Test
    fun `FindServiceJourney should throw exception when not finding a matching servicejourney`() {
        //we should not find a match for the flightcode that does not exist in the servicejourneys
        Assertions.assertThrows(ServiceJourneyNotFoundException::class.java) {
            service.matchServiceJourney(exampleNanFlightcode[0], exampleNanFlightcode[1])
        }
    }

    @Test
    fun `FormatDateTimeZoneToTime should throw exception when invalid date format is given`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            service.matchServiceJourney(exampleNanDate, exampleFlightSasSVG[1])
        }
    }

    @Test
    fun `LogServiceJourneys should make correct logs for servicejourneys`() {
        service.logServiceJourneys()

        val baseDir = File("logs/serviceJourneys")
        //check if example servicejourney log file exists, the filenaming is based on the format used in the logServiceJourneys function, which includes the date, flightcode, and servicejourney id
        val outputFile = baseDir.resolve("SK4011_Mar_Mon_30__SK4011-01-358551288.txt")
        assertTrue(outputFile.exists(), "Output file should exist")
    }

    @Test
    fun `FormatDateTimeZoneToTime should return correct formats`() {
        val formattedDates = service.formatDateTimeZoneToTime(exampleFlightSasSVG[0])

        assertTrue(formattedDates[0] == "08:00:00", "First returned item in list should be HH:mm:ss")
        assertTrue(formattedDates[1] == "May_Tue_05", "Second returned item in list should be english MMM_E_dd format")
    }
}
