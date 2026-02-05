package service

import org.entur.netex.tools.cli.app.FilterNetexApp
import org.entur.netex.tools.lib.config.CliConfig
import org.entur.netex.tools.lib.config.FilterConfig
import org.entur.netex.tools.lib.selectors.entities.EntitySelector
import org.entur.netex.tools.lib.selectors.entities.EntitySelectorContext
import org.entur.netex.tools.lib.selections.EntitySelection
import org.entur.netex.tools.lib.model.Entity
import org.entur.netex.tools.lib.model.EntityModel
import java.io.File
import filter.LineSelector
import model.serviceJourney.ServiceJourney
import model.serviceJourney.ServiceJourneyParser
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FilterExtimeAndFindServiceJourney {


    fun filterExtimeAndWriteResults(lineIds: Set<String>){
        //val lineIds = setOf("AVI:Line:SK_OSL-BGO", "AVI:Line:WF_BGO-EVE")

        println("Filtering for lines: $lineIds\n")

        val filterConfig = FilterConfig(
            entitySelectors = listOf(LineSelector(lineIds)),
            preserveComments = true,
            pruneReferences = true,
            referencesToExcludeFromPruning = setOf(
                "DayType",
                "DayTypeAssignment",
                "DayTypeRef",
                "TimetabledPassingTime",
                "passingTimes",
                "StopPointInJourneyPatternRef"
            ),
            unreferencedEntitiesToPrune = setOf(
                "DayTypeAssignment",
                "JourneyPattern", "Route", "PointOnRoute", "DestinationDisplay"
            )
        )

        //println(">>> FilterConfig has ${filterConfig.entitySelectors.size} selectors <<<")  // DEBUG

        FilterNetexApp(
            cliConfig = CliConfig(alias = mapOf()),
            filterConfig = filterConfig,
            input = File("src/main/kotlin/filter/exampleFiles"),
            target = File("src/main/kotlin/filter/output")
        ).run()

        println("\n=== FILTERING FERDIG ===")
    }

    fun findServiceJourney(): List<ServiceJourney> {
        val parser = ServiceJourneyParser()
        println("=== Parsing folder ===")
        val journeysFromFolder = parser.parseFolder("C:\\Users\\niril\\Documents\\.Uia\\is-314\\gibil\\Gibil\\src\\main\\kotlin\\filter\\output")
        println("Total: ${journeysFromFolder.size} service journeys\n")

        // Process the results
        println("=== Sample results ===")
        journeysFromFolder.forEach { journey ->
            println("Service Journey ID: ${journey.serviceJourneyId}")
            println("Public Code: ${journey.publicCode}")
            println("Departure Time: ${journey.departureTime}")
            println("Arrival Time: ${journey.arrivalTime}")
            println("Day Types: ${journey.dayTypes.size}")
            println()
        }
        return journeysFromFolder
    }

    fun matchServiceJourney(){
        filterExtimeAndWriteResults(setOf("AVI:Line:SK_OSL-BGO", "AVI:Line:WF_BGO-EVE"))
        val serviceJourneys = findServiceJourney()
        serviceJourneys.forEach { journey ->
            println(journey)
        }
    }

    fun formatDateTimeZoneToTime(dateTimeWithZone: String): List<String> {
        val dateTimeWithZone = ZonedDateTime.parse(dateTimeWithZone)
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        val formatFull = DateTimeFormatter.ofPattern("HH:mm:ss")
        val formatMonth = DateTimeFormatter.ofPattern("MM")
        val formatDate = DateTimeFormatter.ofPattern("dd")
        val formatDayName = DateTimeFormatter.ofPattern("E")

        val month = dateTimeWithZone.format(formatMonth)
        val monthCode = months[month.toInt() - 1]

        val dayName = dateTimeWithZone.format(formatDayName)

        val day = dateTimeWithZone.format(formatDate)

        val dayTypeRef = "${monthCode}_${dayName}_${day}"

        return listOf(dateTimeWithZone.format(formatFull), dayTypeRef)
    }
}

fun main(){
    val test = FilterExtimeAndFindServiceJourney()
    test.matchServiceJourney()
    val exFlight = listOf("2026-02-07T13:40:00Z", "SK267", "BGO")
    println(test.formatDateTimeZoneToTime(exFlight[0]))
}