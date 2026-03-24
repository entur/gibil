package service

import io.mockk.mockk
import org.gibil.Dates.daytypeBuilder
import org.gibil.Dates.tomorrowDaytype
import org.gibil.service.ApiService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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

    // midnight edgecase, a fake flight i've manually made, since none existed around midnight
    val exampleMidnight = listOf("2026-02-04T23:30:00Z", "DY9999")
    val exampleLineRefMidnight = listOf("OSL", "TRD")

    val today = Instant.now().atZone(ZoneOffset.UTC)
    val todayNorway = today.withZoneSameInstant(ZoneId.of("Europe/Oslo"))

    // format matching what formatForServiceJourney produces, e.g. "Mar_Tue_24"
    val todayFormatted = daytypeBuilder(today)
    val tomorrowFormatted = tomorrowDaytype()

    @BeforeEach
    fun setup() {
        // write dynamic XML first
        File("src/test/resources/extimeData/test-dynamic.xml").writeText(buildDynamicXml())

        // then init service so it picks up the new file
        service = FindServiceJourneyService(mockk<ApiService>(), "src/test/resources/extimeData")
            .also { it.init() }
    }

    @AfterEach
    fun cleanup() {
        File("src/test/resources/extimeData/test-dynamic.xml").delete()
    }

    @Test
    fun `FindServiceJourney should find service journey match to example flights`() {
        val foundMatch1 = service.matchServiceJourney(exampleFlightSasSVG[0], exampleFlightSasSVG[1], exampleLineRefSas)
        val foundMatch2 = service.matchServiceJourney(exampleFlightNorwegian[0], exampleFlightNorwegian[1], exampleLineRefNorwegian)

        Assertions.assertTrue { "SK4055-01-358551288" in foundMatch1.serviceJourneyId }
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
    fun `MutableServiceJourneyMap should NOT remove journey if needed tomorrow`() {
        service.resetMutableServiceJourneyMap()
        val originalCount = service.mutableServiceJourneyMap.values.flatten().toSet().size

        // today's flight matches the dynamic journey which also has tomorrow's daytype
        val test = service.matchServiceJourney(today.minus(Duration.ofHours(1)).toString(), "DY628", listOf("OSL", "BGO"))

        val actualCount = service.mutableServiceJourneyMap.values.flatten().toSet().size
        Assertions.assertEquals(originalCount, actualCount) // not removed
    }

    @Test
    fun `MutableServiceJourneyMap should remove journey if not needed tomorrow`() {
        service.resetMutableServiceJourneyMap()
        val originalCount = service.mutableServiceJourneyMap.values.flatten().toSet().size

        service.matchServiceJourney(today.minus(Duration.ofHours(1)).toString(), "DY629", listOf("OSL", "BGO"))

        val actualCount = service.mutableServiceJourneyMap.values.flatten().toSet().size
        Assertions.assertEquals(originalCount - 1, actualCount)
    }

    private fun buildDynamicXml(): String {
        val departureTime = today.toString().substring(11, 19) // "HH:mm:ss" from ISO instant
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