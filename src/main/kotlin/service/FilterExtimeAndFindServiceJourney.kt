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

    /**
     * Initializes the filter that filters through extime files to only include the lines we need, and writes the results to a specified output folder.
     * @param lineIds A set of strings representing the line IDs to filter for (e.g., "AVI:Line:SK_OSL-BGO", "AVI:Line:WF_BGO-EVE").
     * @return nothing, but it creates filtered XML files in the specified output folder.
     */
    fun filterExtimeAndWriteResults(lineIds: Set<String>){
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

        FilterNetexApp(
            cliConfig = CliConfig(alias = mapOf()),
            filterConfig = filterConfig,
            input = File("src/main/kotlin/filter/exampleFiles"),
            target = File("src/main/kotlin/filter/output")
        ).run()

        println("\n=== FILTERING FERDIG ===")
    }

    /**
     * Uses ServiceJourneyParser to find service journeys by parsing XML files in a specified folder and extracting relevant information.
     * @return A list of ServiceJourney objects
     */
    fun findServiceJourney(): List<ServiceJourney> {
        val parser = ServiceJourneyParser()
        println("=== Parsing folder ===")
        val journeysFromFolder = parser.parseFolder("C:\\Users\\niril\\Documents\\.Uia\\is-314\\gibil\\Gibil\\src\\main\\kotlin\\filter\\output")
        println("Total: ${journeysFromFolder.size} service journeys\n")

        /*
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
        */
        return journeysFromFolder
    }

    /**
     * Matches a service journey based on date information and flight code.
     * @param dateInfo A list of strings where the first element is the departure time in "HH:mm:ss" format and the second element is a day type reference in the format "MMM_E_dd" (e.g., "Feb_Sat_07").
     * @param flightCode A string representing the flight code (e.g., "SK267").
     * @return A string containing the details of the matched service journey if found, or "none found" if no match is found.
     */
    fun matchServiceJourney(dateInfo: List<String>, flightCode: String): String {
        val serviceJourneys = findServiceJourney()
        serviceJourneys.forEach { journey ->
            val dayTypeMatch = journey.dayTypes.any { dayType ->
                dateInfo[1] in dayType
            }

            val dateInfoMatch = journey.departureTime == dateInfo[0] && dayTypeMatch
            val flightCodeMatch = journey.publicCode == flightCode

            if (dateInfoMatch && flightCodeMatch){
                /*println("=== Found match ===")
                println("Service Journey ID: ${journey.serviceJourneyId}")
                println("Public Code: ${journey.publicCode}")
                println("Departure Time: ${journey.departureTime}")
                println("Arrival Time: ${journey.arrivalTime}")
                println("Day Types: ${journey.dayTypes.size}")*/
                return "=== Found match === Service Journey ID: ${journey.serviceJourneyId} Public Code: ${journey.publicCode} Departure Time: ${journey.departureTime} Arrival Time: ${journey.arrivalTime} Day Types: ${journey.dayTypes.size}"
            } else{
                println("${journey.departureTime} == ${dateInfo[0]} (${journey.departureTime == dateInfo[0]}) and ${dateInfo[1]} in ${journey.dayTypes} (${dateInfo[1] in journey.dayTypes}) and ${journey.publicCode} == ${flightCode} (${journey.publicCode == flightCode})")
            }
        }
        return "none found"
    }

    /**
     * Formats a date-time string with timezone information into a list containing the time and a partial DayType.
     * @param dateTimeWithZone ZonedDateTime object format, a string representing a date and time with timezone information (e.g., "2026-02-07T13:40:00Z").
     * @return A list of strings where the first element is the time in "HH:mm:ss" format and the second element is a day type reference in the format "MMM_E_dd" (e.g., "Feb_Sat_07").
     */
    fun formatDateTimeZoneToTime(dateTimeWithZone: String): List<String> {
        //parse parameter into a ZonedDateTime object
        val dateTimeWithZone = ZonedDateTime.parse(dateTimeWithZone)

        // different formats needed
        val formatFull = DateTimeFormatter.ofPattern("HH:mm:ss")
        val formatMonth = DateTimeFormatter.ofPattern("MMM")
        val formatDate = DateTimeFormatter.ofPattern("dd")
        val formatDayShortName = DateTimeFormatter.ofPattern("E")

        // Implement formats onto object and create partial daytype-value
        val month = dateTimeWithZone.format(formatMonth)
        val dayName = dateTimeWithZone.format(formatDayShortName)
        val day = dateTimeWithZone.format(formatDate)

        val dayType = "${month}_${dayName}_${day}"

        return listOf(dateTimeWithZone.format(formatFull), dayType)
    }
}

fun main(){
    val test = FilterExtimeAndFindServiceJourney()

    //filter extime input data to only include the lines we need
    test.filterExtimeAndWriteResults(setOf("AVI:Line:SK_OSL-BGO", "AVI:Line:WF_BGO-EVE", "AVI:Line:DX_OSL-RRS"))

    //example usage
    val exFlight = listOf("2026-02-07T13:40:00Z", "SK267")
    val exFlight2 = listOf("2026-02-04T09:20:00Z", "DX522")
    val dateInfo = test.formatDateTimeZoneToTime(exFlight[0])
    val dateInfo2 = test.formatDateTimeZoneToTime(exFlight2[0])
    val foundMatch = test.matchServiceJourney(dateInfo, exFlight[1])
    val foundMatch2 = test.matchServiceJourney(dateInfo2, exFlight2[1])

    println(foundMatch)
    println(foundMatch2)
}