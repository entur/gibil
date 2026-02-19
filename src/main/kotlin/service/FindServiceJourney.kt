package service

import java.io.File
import model.serviceJourney.ServiceJourney
import model.serviceJourney.ServiceJourneyParser
import org.gibil.FindServicejourney
import org.gibil.Logger
import org.gibil.service.ApiService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import util.ZipUtil
import util.DateUtil.formatDateTimeZoneToTime

class ServiceJourneyNotFoundException(message: String) : Exception(message)

val debugPrinting = FindServicejourney.DEBUG_PRINTING_FIND_SERVICEJ
val loggingEvents = FindServicejourney.LOGGING_EVENTS_FIND_SERVICEJ

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

            val dateInfoMatch = dateInfo[0] in journey.departureTime && dayTypeMatch
            val flightCodeMatch = journey.publicCode == flightCode

            if (dateInfoMatch && flightCodeMatch) {
                return journey.serviceJourneyId
            } else {
                if (debugPrinting) {
                    println("${journey.departureTime} == ${dateInfo[0]} (${dateInfo[0] in journey.departureTime}) and ${dateInfo[1]} in ${journey.dayTypes} (${dateInfo[1] in journey.dayTypes}) and ${journey.publicCode} == $flightCode (${journey.publicCode == flightCode})")
                }
            }
        }
        throw ServiceJourneyNotFoundException("No service journey found for flight $flightCode at ${dateInfo[0]} on ${dateInfo[1]}")
    }
}