package siri

import model.FlightStop
import model.UnifiedFlight
import org.gibil.Dates
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

    // ── Chain-based multileg path ─────────────────────────────────────────────

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
        try {
            if (departureTimeStr == null) {
                if (flight.isFullyCancelled()) {
                    LOG.debug("Skipping fully cancelled flight {} with no departure time", flight.flightId)
                    return null
                }
                framedVehicleJourneyRef.datedVehicleJourneyRef =
                    "Missing departure time for VehicleJourneyRef: flightId=${flight.flightId}"
                LOG.error("Missing departure time for VehicleJourneyRef: flightId={}", flight.flightId)
            } else {
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
                    if (flight.isFullyCancelled()) {
                        LOG.debug("Skipping fully cancelled flight {} with no matching VJR", flight.flightId)
                        return null
                    }
                    framedVehicleJourneyRef.datedVehicleJourneyRef =
                        "Couldn't validate VehicleJourneyRefID: ${flight.flightId} not found in $findFlightSequence"
                    LOG.error("{}, {}, errors/{}", framedVehicleJourneyRef.datedVehicleJourneyRef, flight.flightId, Dates.currentDateMMMddyyyy())
                }
            }
        } catch (e: Exception) {
            if (flight.isFullyCancelled()) {
                LOG.debug("Skipping fully cancelled flight {} with no matching service journey: {}", flight.flightId, e.message)
                return null
            }
            framedVehicleJourneyRef.datedVehicleJourneyRef =
                "ERROR finding VJR-ID or no match found ${flight.flightId}: ${e.message}"
            LOG.error("Error finding VJR-ID for flightId {}: {}", flight.flightId, e.message)
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
     * Returns true if every stop in the chain that has a scheduled time is cancelled.
     * Used to silently drop fully cancelled flights whose service journey can't be found in ExTime.
     */
    private fun UnifiedFlight.isFullyCancelled(): Boolean {
        val hasAnyTime = stops.any { it.departureTime != null || it.arrivalTime != null }
        if (!hasAnyTime) return false
        return stops.all { stop ->
            (stop.departureTime == null || stop.departureStatusCode == "C") &&
            (stop.arrivalTime == null || stop.arrivalStatusCode == "C")
        }
    }

    private fun applyDepartureStatus(call: EstimatedCall, stop: FlightStop, scheduledZdt: ZonedDateTime) {
        val statusTime = stop.departureStatusTime?.atZone(UTC_ZONE)
        when (stop.departureStatusCode) {
            "D" -> {
                call.departureStatus = CallStatusEnumeration.MISSED
                call.expectedDepartureTime = statusTime ?: scheduledZdt
            }
            "E" -> {
                if (statusTime != null && statusTime == scheduledZdt) {
                    call.departureStatus = CallStatusEnumeration.ON_TIME
                    call.expectedDepartureTime = scheduledZdt
                } else {
                    call.departureStatus = CallStatusEnumeration.DELAYED
                    call.expectedDepartureTime = statusTime ?: scheduledZdt
                }
            }
            "C" -> {
                call.departureStatus = CallStatusEnumeration.CANCELLED
                call.setCancellation(true)
            }
            else -> {
                call.departureStatus = CallStatusEnumeration.ON_TIME
                call.expectedDepartureTime = scheduledZdt
            }
        }
    }

    private fun applyArrivalStatus(call: EstimatedCall, stop: FlightStop, scheduledZdt: ZonedDateTime) {
        val statusTime = stop.arrivalStatusTime?.atZone(UTC_ZONE)
        when (stop.arrivalStatusCode) {
            "A" -> {
                call.arrivalStatus = CallStatusEnumeration.ARRIVED
                call.expectedArrivalTime = statusTime ?: scheduledZdt
            }
            "E" -> {
                if (statusTime != null && statusTime.isBefore(scheduledZdt)) {
                    call.arrivalStatus = CallStatusEnumeration.EARLY
                } else if (statusTime != null && statusTime == scheduledZdt) {
                    call.arrivalStatus = CallStatusEnumeration.ON_TIME
                } else {
                    call.arrivalStatus = CallStatusEnumeration.DELAYED
                }
                call.expectedArrivalTime = statusTime ?: scheduledZdt
            }
            "C" -> {
                call.arrivalStatus = CallStatusEnumeration.CANCELLED
                call.setCancellation(true)
            }
            else -> {
                call.arrivalStatus = CallStatusEnumeration.ON_TIME
                call.expectedArrivalTime = scheduledZdt
            }
        }
    }
}
