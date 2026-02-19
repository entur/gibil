package service

import java.io.File
import model.serviceJourney.ServiceJourney
import model.serviceJourney.ServiceJourneyParser
import org.gibil.service.ApiService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import util.ZipUtil
import util.DateUtil.formatDateTimeZoneToTime
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger(FindServiceJourney::class.java)

class ServiceJourneyNotFoundException(message: String) : Exception(message)

/**
 * @param apiService used to download NeTEx data when running locally
 * @param configuredPath optional override for the NeTEx data directory, set via `gibil.extime.path`
 */
@Component
class FindServiceJourney(
    private val apiService: ApiService,
    @Value("\${gibil.extime.path:#{null}}") private val configuredPath: String?
) {
    val pathBase = configuredPath ?: if (File("/app/extimeData").exists()) "/app/extimeData" else "src/main/resources/extimeData"

    init {
        //if the pathbase is a local pc, and not in k8s in GCP, then download and unzip extime data
        if (pathBase == "src/main/resources/extimeData") {
            ZipUtil.downloadAndUnzip("https://storage.googleapis.com/marduk-dev/outbound/netex/rb_avi-aggregated-netex.zip", "src/main/resources/extimeData", apiService)
        }
    }

    //Makes debug lines for each journey if debug logging is enabled, to give insight into what journeys are being parsed and stored in the serviceJourneyList
    val serviceJourneyList = findServiceJourney().also { journeys ->
        journeys.forEach { journey -> LOG.debug("ServiceJourney: {}", journey) }
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

        LOG.debug("Parsing folder: {}", pathBase)
        val journeysFromFolder = parser.parseFolder(pathBase)

        LOG.debug("Total service journeys found: {}", journeysFromFolder.size)
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
            }
        }

        val codeMatches = serviceJourneyList.filter { it.publicCode == flightCode }
        LOG.debug(
            "No match for {} at {} ({}): {} journeys with same code, departure times: {}",
            flightCode, dateInfo[0], dateInfo[1],
            codeMatches.size,
            codeMatches.map { it.departureTime }
        )
        throw ServiceJourneyNotFoundException("No service journey found for flight $flightCode at ${dateInfo[0]} on ${dateInfo[1]}")
    }
}