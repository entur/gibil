package service

import java.io.File
import model.serviceJourney.ServiceJourney
import model.serviceJourney.ServiceJourneyParser
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.gibil.FindServicejourney
import org.gibil.Logger
import org.gibil.service.ApiService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.gibil.util.ZipUtil
import java.time.ZoneId

class ServiceJourneyNotFoundException(message: String) : Exception(message)

val debugPrinting = FindServicejourney.DEBUG_PRINTING_FIND_SERVICEJ
val loggingEvents = FindServicejourney.LOGGING_EVENTS_FIND_SERVICEJ
val locale = FindServicejourney.LOCALE

/**
 * @param apiService used to download NeTEx data when running locally
 * @param configuredPath optional override for the NeTEx data directory, set via `gibil.extime.path`
 */
@Component
class FindServiceJourney(
    private val apiService: ApiService,
    @Value("\${gibil.extime.path:#{null}}") private val configuredPath: String?
) {
    val pathBase = configuredPath ?: if (File("/app").exists()) "/app" else "src/main/resources/extimeData"

    init {
        //if the pathbase is a local pc, and not in k8s in GCP, then download and unzip extime data
        if (pathBase == "src/main/resources/extimeData") {

            ZipUtil.downloadAndUnzip("https://storage.googleapis.com/marduk-dev/outbound/netex/rb_avi-aggregated-netex.zip", "src/main/resources/extimeData", apiService)
        }

        // This runs after the class is constructed
        if (loggingEvents) {
            logServiceJourneys()
        }
    }

    val serviceJourneyList = findServiceJourney()

    /**
     * logs all servicejourneys in serviceJourneyList to individual .txt files in the logs/serviceJourneys folder, with the filename format: "publicCode_dayType_serviceJourneyId.txt"
     */
    fun logServiceJourneys() {
        val logger = Logger()
        serviceJourneyList.forEach { journey ->
            val filename = "${journey.publicCode}_${journey.dayTypes[0].replace(':', '_').takeLast(10)}_${journey.serviceJourneyId.replace(':', '_').removePrefix("AVI_ServiceJourney")}"
            logger.logMessage(journey.toString(), filename, "serviceJourneys")
        }
    }

    /**
     * Uses ServiceJourneyParser to find service journeys by parsing XML files in a specified folder and extracting relevant information.
     * @return A list of ServiceJourney objects
     * Servicejourney data class contains:
     * - serviceJourneyId: String. The golden reference we want to return when a match is found
     * - dayTypes: List<String>. A list of day type references associated with the service journey
     * - publicCode: String. The flight code associated with the service journey, e.g., "SK267"
     * - departureTime: String. The scheduled departure time of the service journey, what we use to match against with flight code and date
     * - arrivalTime: String. The scheduled arrival time of the service journey
     */
    fun findServiceJourney(): List<ServiceJourney> {
        val parser = ServiceJourneyParser()
        if (debugPrinting) {
            println("=== Parsing folder $pathBase ===")
        }
        val journeysFromFolder = parser.parseFolder(pathBase)
        if (debugPrinting) {
            println("Total: ${journeysFromFolder.size} service journeys\n")
        }

        return journeysFromFolder
    }

    /**
     * Matches a service journey based on date information and flight code.
     * @param dateInfoRaw A string representing the date and time with timezone information (e.g., "2026-02-07T13:40:00Z").
     * @param flightCode A string representing the flight code (e.g., "SK267").
     * @return A string containing the details of the matched service journey if found, or "none found" if no match is found.
     */
    fun matchServiceJourney(dateInfoRaw: String, flightCode: String): String {
        //convert into a list of strings where the first element is the departure time in "HH:mm:ss" format and the second element is a day type reference in the format "MMM_E_dd"
        val dateInfo = formatDateTimeZoneToTime(dateInfoRaw)

        //finding all service journeys and searching through them for a match
        serviceJourneyList.forEach { journey ->
            val dayTypeMatch = journey.dayTypes.any { dayType ->
                dateInfo[1] in dayType
            }

            val dateInfoMatch = journey.departureTime == dateInfo[0] && dayTypeMatch
            val flightCodeMatch = journey.publicCode == flightCode

            if (dateInfoMatch && flightCodeMatch) {
                return journey.serviceJourneyId
            } else {
                if (debugPrinting) {
                    println("${journey.departureTime} == ${dateInfo[0]} (${journey.departureTime == dateInfo[0]}) and ${dateInfo[1]} in ${journey.dayTypes} (${dateInfo[1] in journey.dayTypes}) and ${journey.publicCode} == $flightCode (${journey.publicCode == flightCode})")
                }
            }
        }
        throw ServiceJourneyNotFoundException("No service journey found for flight $flightCode at ${dateInfo[0]} on ${dateInfo[1]}")
    }

    /**
     * Formats a date-time string with timezone information into a list containing the time and a partial DayType.
     * @param dateTimeString string representing a date and time with timezone information (e.g., "2026-02-07T13:40:00Z").
     * @return A list of strings where the first element is the time in "HH:mm:ss" format and the second element is a day type reference in the format "MMM_E_dd" (e.g., "Feb_Sat_07").
     */
    fun formatDateTimeZoneToTime(dateTimeString: String): List<String> {
        try {
            //parse parameter into a ZonedDateTime object
            val dateTimeWithZone = ZonedDateTime.parse(dateTimeString)

            // Norwegian timezone
            val norwayZone = ZoneId.of("Europe/Oslo")

            // Convert to Norwegian timezone
            val norwayDateTime = dateTimeWithZone.withZoneSameInstant(norwayZone)

            // different formats needed, with locale to ensure month and day names are in English, as the day type references in the service journeys are in English
            val formatFull = DateTimeFormatter.ofPattern("HH:mm:ss", locale)
            val formatMonth = DateTimeFormatter.ofPattern("MMM", locale)
            val formatDate = DateTimeFormatter.ofPattern("dd", locale)
            val formatDayShortName = DateTimeFormatter.ofPattern("E", locale)

            // Implement formats onto object and create partial daytyperef-value
            val month = dateTimeWithZone.format(formatMonth)
            val dayName = dateTimeWithZone.format(formatDayShortName)
            val day = dateTimeWithZone.format(formatDate)

            val dayType = "${month}_${dayName}_${day}"

            val norwegianDepartureTime = norwayDateTime.format(formatFull)

            return listOf(norwegianDepartureTime, dayType)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date-time format: $dateTimeString. Expected format: ISO 8601 (e.g., 2026-02-07T13:40:00Z)", e ) }
    }
}