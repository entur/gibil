package siri

import model.FlightStop
import model.UnifiedFlight
import org.gibil.Dates
import org.gibil.FlightCodes
import org.gibil.SiriConfig
import org.gibil.service.AirportQuayService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import service.FindServiceJourneyService
import uk.org.siri.siri21.*
import util.AirportSizeClassification.orderAirportsBySize
import java.math.BigInteger
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs

private val LOG = LoggerFactory.getLogger(SiriETMapper::class.java)

/**
 * Maps [UnifiedFlight] chains into SIRI Estimated Timetable (ET) documents.
 * Resolves airport quay IDs via [AirportQuayService], looks up ExTime service journey
 * references via [FindServiceJourneyService], and translates Avinor status codes
 * into SIRI [CallStatusEnumeration] values.
 *
 * Flights that cannot be matched to a valid service journey reference are excluded
 * from the output rather than emitted with placeholder error values.
 */
@Service
class SiriETMapper(
    private val airportQuayService: AirportQuayService,
    private val findServiceJourneyService: FindServiceJourneyService
) {

    companion object {
        private const val PRODUCER_REF = "AVINOR"
        private const val DATA_SOURCE = "AVINOR"
        private const val OPERATOR_PREFIX = "AVI:Operator:"
        private const val LINE_PREFIX = "AVI:Line:"
        private const val STOP_POINT_REF_PREFIX = "AVI:StopPointRef:"
        private val UTC_ZONE = ZoneOffset.UTC
    }

    /**
     * Extension function that generates a deterministic numeric ID hash from a String.
     * Uses a variant of the DJB2 algorithm, matching ExTime's route ID format.
     *
     * @param length The max number of digits in the returned hash string.
     * @return A numeric string of up to [length] digits derived from hashing this String.
     */
    private fun String.idHash(length: Int): String {
        val hashcode = fold(0) { acc, char -> (acc shl 5) - acc + char.code }
        return abs(hashcode).toString().take(length)
    }

    /**
     * Maps a list of [UnifiedFlight] chains into a SIRI EstimatedTimetableDelivery.
     * Used by both the /siri endpoint and the subscription polling path.
     *
     * @param flights The list of unified flights to map.
     * @return A [Siri] document with all non-skipped flights as EstimatedVehicleJourneys.
     */
    fun mapUnifiedFlightsToSiri(flights: List<UnifiedFlight>): Siri {
        val siri = Siri()

        val serviceDelivery = ServiceDelivery()
        serviceDelivery.responseTimestamp = Dates.instantNowUtc()

        val producerRef = RequestorRef()
        producerRef.value = PRODUCER_REF
        serviceDelivery.producerRef = producerRef

        val estimatedTimetableDelivery = EstimatedTimetableDeliveryStructure()
        estimatedTimetableDelivery.version = SiriConfig.SIRI_VERSION_DELIVERY
        estimatedTimetableDelivery.responseTimestamp = Dates.instantNowUtc()

        val estimatedVersionFrame = EstimatedVersionFrameStructure()
        estimatedVersionFrame.recordedAtTime = Dates.instantNowUtc()

        flights.forEach { flight ->
            mapFlightToJourney(flight)?.let { estimatedVersionFrame.estimatedVehicleJourneies.add(it) }
        }

        estimatedTimetableDelivery.estimatedJourneyVersionFrames.add(estimatedVersionFrame)
        serviceDelivery.estimatedTimetableDeliveries.add(estimatedTimetableDelivery)
        siri.serviceDelivery = serviceDelivery

        return siri
    }

    /**
     * Maps a single [UnifiedFlight] to a SIRI [EstimatedVehicleJourney].
     *
     * Returns null (and logs an error) if the first stop has no departure time or if no
     * valid service journey reference can be found in ExTime, so the caller can silently
     * exclude the flight from the output.
     *
     * @param flight The unified flight chain to map.
     * @return The mapped journey, or null if the flight should be excluded from the output.
     */
    private fun mapFlightToJourney(flight: UnifiedFlight): EstimatedVehicleJourney? {
        val journey = EstimatedVehicleJourney()

        // LineRef uses size-ordered airports (largest first)
        val ordered = orderAirportsBySize(listOf(flight.origin, flight.destination))
        val lineRef = LineRef()
        lineRef.value = "$LINE_PREFIX${flight.operator}_${ordered[0]}-${ordered[1]}"
        journey.lineRef = lineRef

        val operatorRef = OperatorRefStructure()
        operatorRef.value = "$OPERATOR_PREFIX${flight.operator}"
        journey.operatorRef = operatorRef

        val directionRef = DirectionRefStructure()
        // Circular routes (origin == destination) default to "outbound".
        // Otherwise outbound if departing the larger (hub) airport, inbound otherwise.
        directionRef.value = if (flight.origin == flight.destination || flight.origin == ordered[0]) "outbound" else "inbound"
        journey.directionRef = directionRef

        val framedVehicleJourneyRef = FramedVehicleJourneyRefStructure()
        val dataFrameRef = DataFrameRefStructure()
        dataFrameRef.value = flight.date.toString()
        framedVehicleJourneyRef.dataFrameRef = dataFrameRef

        // VJR hash uses the full stop sequence (not size-sorted) to match ExTime's route ID format
        val fullRoute = "${flight.operator}_${flight.stops.joinToString("-") { it.airportCode }}"
        val routeHash = fullRoute.idHash(10)

        val departureTimeStr = flight.stops.first().departureTime?.atZone(UTC_ZONE)?.toString()
        if (departureTimeStr == null) {
            LOG.error("Missing departure time for VehicleJourneyRef: flightId={}", flight.flightId)
            return null
        }
        try {
            val findFlightSequence =
                findServiceJourneyService.matchServiceJourney(departureTimeStr, flight.flightId)
            if (flight.flightId in findFlightSequence) {
                framedVehicleJourneyRef.datedVehicleJourneyRef = findFlightSequence
                // Route hash check is advisory for multi-leg/circular flights:
                // our stop sequence may differ from ExTime's route format
                if (routeHash !in findFlightSequence) {
                    LOG.debug("Route hash mismatch for {} (expected {} in {}), using service journey anyway",
                        flight.flightId, routeHash, findFlightSequence)
                }
            } else {
                LOG.error("No VJR match for {}, errors/{}", flight.flightId, Dates.currentDateMMMddyyyy())
                return null
            }
        } catch (e: Exception) {
            LOG.error("Error finding VJR-ID for flightId {}: {}", flight.flightId, e.message)
            return null
        }

        journey.framedVehicleJourneyRef = framedVehicleJourneyRef
        journey.dataSource = DATA_SOURCE
        journey.isIsCompleteStopSequence = true

        val estimatedCallsWrapper = EstimatedVehicleJourney.EstimatedCalls()
        journey.setEstimatedCalls(estimatedCallsWrapper)

        flight.stops.forEachIndexed { index, stop ->
            val call = EstimatedCall()

            val stopPointRef = StopPointRefStructure()
            stopPointRef.value = airportQuayService.getQuayId(stop.airportCode)
                ?: "$STOP_POINT_REF_PREFIX${stop.airportCode}"
            call.stopPointRef = stopPointRef
            call.order = BigInteger.valueOf((index + 1).toLong())

            //when stop.departureTime and stop.arrivalTime are null, the stop is skipped in ExTime, so we can skip it here as well
            if (stop.departureTime != null) {
                val departureZdt = stop.departureTime.atZone(UTC_ZONE)
                call.aimedDepartureTime = departureZdt
                applyDepartureStatus(call, stop, departureZdt)
            }

            if (stop.arrivalTime != null) {
                val arrivalZdt = stop.arrivalTime.atZone(UTC_ZONE)
                call.aimedArrivalTime = arrivalZdt
                applyArrivalStatus(call, stop, arrivalZdt)
            }

            estimatedCallsWrapper.estimatedCalls.add(call)
        }

        return journey
    }

    /**
     * Sets departure status and expected departure time on [call] based on the Avinor status code.
     *
     * Status mappings:
     * - [FlightCodes.DEPARTED_CODE] ("D") → MISSED — the flight has already left.
     * - [FlightCodes.NEW_TIME_CODE] ("E") → ON_TIME if the new time matches scheduled, DELAYED otherwise.
     * - [FlightCodes.CANCELLED_CODE] ("C") → CANCELLED.
     * - No status / unknown → ON_TIME with the scheduled time as the expected time.
     *
     * @param call The [EstimatedCall] to update.
     * @param stop The [FlightStop] providing the status code and status time.
     * @param scheduledZdt The aimed (scheduled) departure time, used as fallback when no status time is available.
     */
    private fun applyDepartureStatus(call: EstimatedCall, stop: FlightStop, scheduledZdt: ZonedDateTime) {
        val statusTime = stop.departureStatusTime?.atZone(UTC_ZONE)
        when (stop.departureStatusCode) {
            FlightCodes.DEPARTED_CODE -> {
                call.departureStatus = CallStatusEnumeration.MISSED
                call.expectedDepartureTime = statusTime ?: scheduledZdt
            }
            FlightCodes.NEW_TIME_CODE -> {
                if (statusTime != null && statusTime == scheduledZdt) {
                    call.departureStatus = CallStatusEnumeration.ON_TIME
                    call.expectedDepartureTime = scheduledZdt
                } else {
                    call.departureStatus = CallStatusEnumeration.DELAYED
                    call.expectedDepartureTime = statusTime ?: scheduledZdt
                }
            }
            FlightCodes.CANCELLED_CODE -> {
                call.departureStatus = CallStatusEnumeration.CANCELLED
                call.setCancellation(true)
                call.expectedDepartureTime = scheduledZdt
            }
            else -> {
                call.departureStatus = CallStatusEnumeration.ON_TIME
                call.expectedDepartureTime = scheduledZdt
            }
        }
    }

    /**
     * Sets arrival status and expected arrival time on [call] based on the Avinor status code.
     *
     * Status mappings:
     * - [FlightCodes.ARRIVED_CODE] ("A") → ARRIVED.
     * - [FlightCodes.NEW_TIME_CODE] ("E") → EARLY if before scheduled, ON_TIME if equal, DELAYED otherwise.
     * - [FlightCodes.CANCELLED_CODE] ("C") → CANCELLED.
     * - No status / unknown → ON_TIME with the scheduled time as the expected time.
     *
     * @param call The [EstimatedCall] to update.
     * @param stop The [FlightStop] providing the status code and status time.
     * @param scheduledZdt The aimed (scheduled) arrival time, used as fallback when no status time is available.
     */
    private fun applyArrivalStatus(call: EstimatedCall, stop: FlightStop, scheduledZdt: ZonedDateTime) {
        val statusTime = stop.arrivalStatusTime?.atZone(UTC_ZONE)
        when (stop.arrivalStatusCode) {
            FlightCodes.ARRIVED_CODE -> {
                call.arrivalStatus = CallStatusEnumeration.ARRIVED
                call.expectedArrivalTime = statusTime ?: scheduledZdt
            }
            FlightCodes.NEW_TIME_CODE -> {
                if (statusTime != null && statusTime.isBefore(scheduledZdt)) {
                    call.arrivalStatus = CallStatusEnumeration.EARLY
                } else if (statusTime != null && statusTime == scheduledZdt) {
                    call.arrivalStatus = CallStatusEnumeration.ON_TIME
                } else {
                    call.arrivalStatus = CallStatusEnumeration.DELAYED
                }
                call.expectedArrivalTime = statusTime ?: scheduledZdt
            }
            FlightCodes.CANCELLED_CODE -> {
                call.arrivalStatus = CallStatusEnumeration.CANCELLED
                call.setCancellation(true)
                call.expectedArrivalTime = scheduledZdt
            }
            else -> {
                call.arrivalStatus = CallStatusEnumeration.ON_TIME
                call.expectedArrivalTime = scheduledZdt
            }
        }
    }
}