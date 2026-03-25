package service

import jakarta.annotation.PostConstruct
import java.io.File
import model.serviceJourney.ServiceJourney
import handler.ServiceJourneyParser
import org.gibil.Dates.tomorrowDaytype
import org.gibil.service.ApiService
import org.springframework.beans.factory.annotation.Value
import util.ZipUtil
import util.DateUtil.formatForServiceJourney
import org.slf4j.LoggerFactory
import org.gibil.FindServiceJourneyConstants
import org.springframework.stereotype.Service

private val LOG = LoggerFactory.getLogger(FindServiceJourneyService::class.java)

class ServiceJourneyNotFoundException(message: String) : Exception(message)

/**
 * @param apiService used to download NeTEx data when running locally
 * @param configuredPath optional override for the NeTEx data directory, set via `gibil.extime.path`
 */
@Service
class FindServiceJourneyService(
    private val apiService: ApiService,
    @Value("\${org.gibil.extime.data-file:#{null}}") private val configuredPath: String?
) {
    val pathBase = configuredPath ?: if (File(FindServiceJourneyConstants.CLOUD_BASEPATH).exists()) FindServiceJourneyConstants.CLOUD_BASEPATH else FindServiceJourneyConstants.LOCAL_BASEPATH

    lateinit var serviceJourneyList: List<ServiceJourney>

    @PostConstruct
    fun init() {
        //if the pathbase is a local pc, and not in k8s in GCP, then download and unzip extime data
        if (pathBase == FindServiceJourneyConstants.LOCAL_BASEPATH) {
            ZipUtil.downloadAndUnzip("https://storage.googleapis.com/marduk-dev/outbound/netex/rb_avi-aggregated-netex.zip", FindServiceJourneyConstants.LOCAL_BASEPATH, apiService)
        }
        //Makes debug lines for each journey if debug logging is enabled, to give insight into what journeys are being parsed and stored in the serviceJourneyList
        serviceJourneyList = findServiceJourney().also { journeys ->
            journeys.forEach { journey -> LOG.debug("ServiceJourney: {}", journey) }
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

        LOG.debug("Parsing folder: {}", pathBase)
        val journeysFromFolder = parser.parseFolder(pathBase)

        LOG.debug("Total service journeys found: {}", journeysFromFolder.size)
        return journeysFromFolder
    }

    /**
     * Matches a service journey based on date information and flight code.
     * @param departureInfoRaw A string representing the date and time with timezone information (e.g., "2026-02-07T13:40:00Z").
     * @param flightCode A string representing the flight code (e.g., "SK267").
     * @return A string containing the details of the matched service journey if found, or "none found" if no match is found.
     */
    fun matchServiceJourney(
        workingMap: MutableMap<String, MutableList<ServiceJourney>>,
        departureInfoRaw: String,
        flightCode: String,
        lineRefInfo: List<String>): ServiceJourney {

        val avinorFlightDateInfo = formatForServiceJourney(departureInfoRaw)
        val key = buildKey(flightCode, avinorFlightDateInfo[0])
        val bucket = workingMap[key]

        if (!bucket.isNullOrEmpty()) {
            val matchIndex = bucket.indexOfFirst { journey ->
                val dayTypeMatch = journey.dayTypes.any { avinorFlightDateInfo[1] in it }
                val lineRef = journey.lineRef
                val lineRefMatch = lineRef != null && lineRefInfo[0] in lineRef && lineRefInfo[1] in lineRef
                if (!lineRefMatch && dayTypeMatch) {
                    LOG.warn("lineref not match, but flightcode and dateinfo matched; {}, {}, {}, airports from avinor: {}, {}, lineref in extime lineref: {}",
                        flightCode, avinorFlightDateInfo[0], avinorFlightDateInfo[1], lineRefInfo[0], lineRefInfo[1], lineRef)
                }
                dayTypeMatch && lineRefMatch
            }
            if (matchIndex != -1) {
                val matched = bucket[matchIndex]

                val daytypeTomorrow = tomorrowDaytype()
                val neededTomorrow = matched.dayTypes.any { daytypeTomorrow in it }

                if (!neededTomorrow) {
                    bucket.removeAt(matchIndex)
                    matched.departureTime.forEach { depTime ->
                        val otherKey = buildKey(matched.publicCode, depTime)
                        workingMap[otherKey]?.remove(matched)
                    }
                }

                return matched
            }
        }

        val codeMatches = serviceJourneyList.filter { it.publicCode == flightCode }
        LOG.debug(
            "No match for {} at {} ({}): {} journeys with same code, departure times: {}",
            flightCode, avinorFlightDateInfo[0], avinorFlightDateInfo[1],
            codeMatches.size,
            codeMatches.map { it.departureTime }
        )
        throw ServiceJourneyNotFoundException("No service journey found for flight $flightCode at ${avinorFlightDateInfo[0]} on ${avinorFlightDateInfo[1]}")
    }

    /**
     * Resets the resetMutableServiceJourneyList to contain the servicejourneys from extime.
     * Needs to be done before servicejourney matching is started
     */
    fun buildWorkingMap(): MutableMap<String, MutableList<ServiceJourney>> {
        val map = mutableMapOf<String, MutableList<ServiceJourney>>()
        serviceJourneyList.forEach { journey ->
            journey.departureTime.forEach { depTime ->
                val key = buildKey(journey.publicCode, depTime)
                map.getOrPut(key) { mutableListOf() }.add(journey)
            }
        }
        return map
    }

    private fun buildKey(publicCode: String, departureTime: String) = "$publicCode|$departureTime"
}