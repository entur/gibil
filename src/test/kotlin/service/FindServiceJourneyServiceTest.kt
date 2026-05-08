package service

import io.mockk.mockk
import org.gibil.util.Dates.daytypeBuilder
import org.gibil.util.Dates.tomorrowDaytype
import org.gibil.service.ApiService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.Test

class FindServiceJourneyServiceTest {
    lateinit var service: FindServiceJourneyService

    //correct information
    val exampleFlightSasSVG = listOf("2026-03-23T19:30:00Z", "SK4055")
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

    // midnight edgecase, two fake flights i've manually made, since none existed around midnight
    val exampleMidnightSummertime = listOf("2026-05-24T22:30:00Z", "DY9999")
    val exampleLineRefMidnightSummertime = listOf("OSL", "TRD")

    val exampleMidnightWintertime = listOf("2026-02-24T23:30:00Z", "DY9999")
    val exampleLineRefMidnightWintertime = listOf("OSL", "TRD")

    val today = Instant.now().atZone(ZoneOffset.UTC)

    // format matching what formatForServiceJourney produces, e.g. "Mar_Tue_24"
    val todayFormatted = daytypeBuilder(today)
    val tomorrowFormatted = tomorrowDaytype()

    val norwayTime = today
        .withZoneSameInstant(ZoneId.of("Europe/Oslo"))
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    @BeforeEach
    fun setup() {
        // write dynamic XML first
        File("src/test/resources/extimeData/test-dynamic.xml").writeText(buildDynamicXml())

        // then init service so it picks up the new file
        service = FindServiceJourneyService(mockk<ApiService>(), "https://mock-url", "src/test/resources/extimeData")
            .also { it.init() }
    }

    @AfterEach
    fun cleanup() {
        File("src/test/resources/extimeData/test-dynamic.xml").delete()
    }

    @Test
    fun `FindServiceJourney should find service journey match to example flights`() {
        val workingMap = service.buildWorkingMap()

        val foundMatch1 = service.matchServiceJourney(workingMap, exampleFlightSasSVG[0], exampleFlightSasSVG[1], exampleLineRefSas)
        val foundMatch2 = service.matchServiceJourney(workingMap, exampleFlightNorwegian[0], exampleFlightNorwegian[1], exampleLineRefNorwegian)

        Assertions.assertTrue { "SK4055-01-358551288" in foundMatch1.serviceJourneyId }
        Assertions.assertTrue { "DY628-01-523288933" in foundMatch2.serviceJourneyId }
    }

    @Test
    fun `FindServiceJourney should throw exception when not finding a matching servicejourney`() {
        val workingMap = service.buildWorkingMap()

        //we should not find a match for the flightcode that does not exist in the servicejourneys
        Assertions.assertThrows(ServiceJourneyNotFoundException::class.java) {
            service.matchServiceJourney(workingMap, exampleNanFlightcode[0], exampleNanFlightcode[1], exampleLineNanFlight)
        }
    }

    @Test
    fun `FindServiceJourney should throw when date does not match even if flight code and lineref exists`() {
        val workingMap = service.buildWorkingMap()

        Assertions.assertThrows(ServiceJourneyNotFoundException::class.java) {
            service.matchServiceJourney(workingMap, exampleWrongDate[0], exampleWrongDate[1], exampleLineRefSas)
        }
    }

    @Test
    fun `FindServiceJourney should throw when airports from avinor dosent match lineref info`() {
        val workingMap = service.buildWorkingMap()

        Assertions.assertThrows(ServiceJourneyNotFoundException::class.java) {
            service.matchServiceJourney(workingMap, exampleFlightSasSVG[0], exampleFlightSasSVG[1], exampleWrongLineRef)
        }
    }

    @Test
    fun `FindServiceJourney should find ID when flight is around midnight during summertime`() {
        val workingMap = service.buildWorkingMap()

        val foundMatch = service.matchServiceJourney(workingMap, exampleMidnightSummertime[0], exampleMidnightSummertime[1], exampleLineRefMidnightSummertime)

        Assertions.assertTrue { "DY9999-01-123456789" in foundMatch.serviceJourneyId }
    }

    @Test
    fun `FindServiceJourney should find ID when flight is around midnight during winter time`() {
        val workingMap = service.buildWorkingMap()

        val foundMatch = service.matchServiceJourney(workingMap, exampleMidnightWintertime[0], exampleMidnightWintertime[1], exampleLineRefMidnightWintertime)

        Assertions.assertTrue { "DY9999-01-123456789" in foundMatch.serviceJourneyId }
    }

    @Test
    fun `WorkingMap should NOT remove journey if needed tomorrow`() {
        val workingMap = service.buildWorkingMap()

        val originalCount = workingMap.values.flatten().toSet().size

        // today's flight matches the dynamic journey which also has tomorrow's daytype
        service.matchServiceJourney(workingMap, norwayTime,"DY628", listOf("OSL", "BGO"))

        val actualCount = workingMap.values.flatten().toSet().size
        Assertions.assertEquals(originalCount, actualCount) // not removed
    }

    @Test
    fun `WorkingMap should remove journey if not needed tomorrow`() {
        val workingMap = service.buildWorkingMap()
        val originalCount = workingMap.values.flatten().toSet().size

        service.matchServiceJourney(workingMap, norwayTime, "DY629", listOf("OSL", "BGO"))

        val actualCount = workingMap.values.flatten().toSet().size
        Assertions.assertEquals(originalCount - 1, actualCount)
    }

    @Test
    fun `buildWorkingMap should return fresh full map each time`() {
        val map1 = service.buildWorkingMap()

        service.matchServiceJourney(map1, exampleFlightSasSVG[0], exampleFlightSasSVG[1], exampleLineRefSas)

        val map2 = service.buildWorkingMap()

        //maps size shouldnt match, since the matchservicejourney on map1 should remove the exampleflight
        Assertions.assertFalse(
            map2.values.flatten().toSet().size ==
            map1.values.flatten().toSet().size
        )
    }

    @Test
    fun `Matching a tomorrow-needed journey twice should not corrupt the map`() {
        val workingMap = service.buildWorkingMap()
        val countBefore = workingMap.values.flatten().toSet().size

        service.matchServiceJourney(workingMap, norwayTime, "DY628", listOf("OSL", "BGO"))
        service.matchServiceJourney(workingMap, norwayTime, "DY628", listOf("OSL", "BGO"))

        val countAfter = workingMap.values.flatten().toSet().size
        Assertions.assertEquals(countBefore, countAfter)
    }

    private fun buildDynamicXml(): String {
        val norwayZone = ZoneId.of("Europe/Oslo")
        val departureTime = today
            .withZoneSameInstant(norwayZone)
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <PublicationDelivery xmlns="http://www.netex.org.uk/netex">
          <dataObjects>
            <CompositeFrame>
              <frames>
                <TimetableFrame>
                  <vehicleJourneys>
                    <ServiceJourney version="1" id="AVI:ServiceJourney:DY628-DYNAMIC-523288933">
                      <dayTypes>
                        <DayTypeRef ref="AVI:DayType:999-$todayFormatted"/>
                        <DayTypeRef ref="AVI:DayType:999-$tomorrowFormatted"/>
                      </dayTypes>
                      <PublicCode>DY628</PublicCode>
                      <LineRef ref="AVI:Line:DY_OSL-BGO"/>
                      <passingTimes>
                        <TimetabledPassingTime version="1" id="AVI:TimetabledPassingTime:dynamic-1">
                          <DepartureTime>$departureTime</DepartureTime>
                        </TimetabledPassingTime>
                      </passingTimes>
                    </ServiceJourney>
                    <ServiceJourney version="1" id="AVI:ServiceJourney:DY629-DYNAMIC-523288933">
                      <dayTypes>
                        <DayTypeRef ref="AVI:DayType:999-$todayFormatted"/>
                      </dayTypes>
                      <PublicCode>DY629</PublicCode>
                      <LineRef ref="AVI:Line:DY_OSL-BGO"/>
                      <passingTimes>
                        <TimetabledPassingTime version="1" id="AVI:TimetabledPassingTime:dynamic-1">
                          <DepartureTime>$departureTime</DepartureTime>
                        </TimetabledPassingTime>
                      </passingTimes>
                    </ServiceJourney>
                  </vehicleJourneys>
                </TimetableFrame>
              </frames>
            </CompositeFrame>
          </dataObjects>
        </PublicationDelivery>
    """.trimIndent()
    }
}