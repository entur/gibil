package siri

import model.FlightStop
import model.UnifiedFlight
import model.xmlFeedApi.Airport
import model.xmlFeedApi.Flight
import org.gibil.Dates
import org.gibil.FlightCodes
import org.gibil.SiriConfig
import org.gibil.service.AirportQuayService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import service.FindServiceJourneyService
import uk.org.siri.siri21.*
import util.AirportSizeClassification.orderAirportsBySize
import util.DateUtil.parseTimestamp
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
        // Constants for SIRI mapping
        private const val PRODUCER_REF = "AVINOR"
        private const val DATA_SOURCE = "AVINOR"
        // SIRI reference prefixes
        private const val OPERATOR_PREFIX = "AVI:Operator:"
        private const val LINE_PREFIX = "AVI:Line:"
        private const val VEHICLE_JOURNEY_PREFIX = "AVI:DatedVehicleJourneyRef:"
        private const val STOP_POINT_REF_PREFIX = "AVI:StopPointRef:"
        private val UTC_ZONE = ZoneOffset.UTC
    }

    //Create SIRI element, populate header and add EstimatedTimetableDelivery to SIRI response
    fun mapToSiri(
        airport: Airport,
        requestingAirportCode: String
    ): Siri {
        val siri = Siri()

        val serviceDelivery = ServiceDelivery()
        serviceDelivery.responseTimestamp = ZonedDateTime.now()

        val producerRef = RequestorRef()
        producerRef.value = PRODUCER_REF
        serviceDelivery.producerRef = producerRef

        val etDelivery = createEstimatedTimetableDelivery(airport, requestingAirportCode)
        serviceDelivery.estimatedTimetableDeliveries.add(etDelivery)

        siri.serviceDelivery = serviceDelivery
        return siri
    }

    /**
     * Maps a collection of merged flights to a SIRI document.
     * Used by AvinorPollingService to push changed flights to subscribers.
     * @param mergedFlights Collection of flights that have been merged across airports
     * @return SIRI document with EstimatedCalls for all changed flights
     */
    fun mapMergedFlightsToSiri(mergedFlights: Collection<Flight>): Siri {
        val siri = Siri()

        val serviceDelivery = ServiceDelivery()
        serviceDelivery.responseTimestamp = Dates.instantNowUtc()

        val producerRef = RequestorRef()
        producerRef.value = PRODUCER_REF
        serviceDelivery.producerRef = producerRef

        val delivery = EstimatedTimetableDeliveryStructure()
        delivery.version = SiriConfig.SIRI_VERSION_DELIVERY
        delivery.responseTimestamp = ZonedDateTime.now()

        val estimatedVersionFrame = EstimatedVersionFrameStructure()
        estimatedVersionFrame.recordedAtTime = Dates.instantNowUtc()

        mergedFlights.forEach { flight ->
            val contextAirport = flight.departureAirport ?: flight.arrivalAirport ?: return@forEach
            val evj = mapFlightToEstimatedVehicleJourney(flight, contextAirport)
            if (evj != null) {
                estimatedVersionFrame.estimatedVehicleJourneies.add(evj)
            }
        }

        delivery.estimatedJourneyVersionFrames.add(estimatedVersionFrame)
        serviceDelivery.estimatedTimetableDeliveries.add(delivery)
        siri.serviceDelivery = serviceDelivery
        return siri
    }

    private fun createEstimatedTimetableDelivery(
        airport: Airport,
        requestingAirportCode: String
    ): EstimatedTimetableDeliveryStructure {

        val delivery = EstimatedTimetableDeliveryStructure()
        delivery.version = SiriConfig.SIRI_VERSION_DELIVERY
        delivery.responseTimestamp = ZonedDateTime.now()

        // create EstimatedJourneyVersionFrame element
        val estimatedVersionFrame = EstimatedVersionFrameStructure()
        estimatedVersionFrame.recordedAtTime = parseTimestamp(airport.flightsContainer?.lastUpdate) ?: ZonedDateTime.now()

        // Map each flight to EstimatedVehicleJourney
        airport.flightsContainer?.flight?.forEach { flight ->
            val estimatedVehicleJourney = mapFlightToEstimatedVehicleJourney(flight, requestingAirportCode)
            if (estimatedVehicleJourney != null) {
                estimatedVersionFrame.estimatedVehicleJourneies.add(estimatedVehicleJourney)
            }
        }
        delivery.estimatedJourneyVersionFrames.add(estimatedVersionFrame)
        return delivery
    }

    private fun mapFlightToEstimatedVehicleJourney(
        flight: Flight,
        requestingAirportCode: String
    ): EstimatedVehicleJourney? {

        // Skip flights without a flightId
        if (flight.flightId == null) return null
        val airline = flight.airline ?: return null

        // For merged flights, use scheduledDepartureTime or scheduledArrivalTime; otherwise use scheduleTime
        val scheduleTime = parseTimestamp(flight.scheduledDepartureTime)
            ?: parseTimestamp(flight.scheduledArrivalTime)
            ?: parseTimestamp(flight.scheduleTime)
            ?: return null

        val estimatedVehicleJourney = EstimatedVehicleJourney()

        //Set lineRef
        val lineRef = LineRef()
        val orderedRoute = routeBuilder(requestingAirportCode, flight, true)
        lineRef.value = "$LINE_PREFIX$orderedRoute"
        estimatedVehicleJourney.lineRef = lineRef

        //Set directionRef - for merged flights, check if departure airport matches context
        val directionRef = DirectionRefStructure()
        directionRef.value = if (flight.isMerged) {
            if (flight.departureAirport == requestingAirportCode) "outbound" else "inbound"
        } else {
            if (flight.isDeparture()) "outbound" else "inbound"
        }
        estimatedVehicleJourney.directionRef = directionRef

        // Set FramedVehicleJourneyRef
        val framedVehicleJourneyRef = FramedVehicleJourneyRefStructure()
        val dataFrameRef = DataFrameRefStructure()
        dataFrameRef.value = scheduleTime.toLocalDate().toString()
        framedVehicleJourneyRef.dataFrameRef = dataFrameRef

        val fullRoute = routeBuilder(requestingAirportCode, flight, false)
        val routeCodeId = fullRoute.idHash(10)

        //datevehiclejourneyref fetching and evaluation
        val flightId = flight.flightId
        val scheduledDepartureTime = flight.scheduledDepartureTime
        try {
            // Check for null values before calling matchServiceJourney
            if (scheduledDepartureTime == null || flightId == null) {
                framedVehicleJourneyRef.datedVehicleJourneyRef = "Missing required flight data for VehicleJourneyRef: scheduledDepartureTime=$scheduledDepartureTime, flightId=$flightId"
            } else{

                //calls matchServiceJourney with flightId and scheduledDepartureTime to find the corresponding service journey sequence
                    //if none is found an exception will be thrown, which is caught in the catch
                val findFlightSequence =
                    findServiceJourneyService.matchServiceJourney(scheduledDepartureTime, flightId)

                //a match was found
                if (flightId in findFlightSequence) {
                    framedVehicleJourneyRef.datedVehicleJourneyRef = findFlightSequence
                    if (routeCodeId !in findFlightSequence) {
                        LOG.debug("Route hash mismatch for {} (routeHash {} not in {})", flightId, routeCodeId, findFlightSequence)
                    }
                } else {
                    framedVehicleJourneyRef.datedVehicleJourneyRef = "Couldn't validate VehicleJourneyRefID: $flightId not found in $findFlightSequence"
                    LOG.error("{}, {}, errors/{}", framedVehicleJourneyRef.datedVehicleJourneyRef, flightId, Dates.currentDateMMMddyyyy())
                }
            }
        } catch (e: Exception) {
            framedVehicleJourneyRef.datedVehicleJourneyRef = "ERROR finding VJR-ID or no match found $flightId: ${e.message}"

            LOG.error("Error finding VJR-ID for flightId {}: {}", flightId, e.message)

        }

        estimatedVehicleJourney.framedVehicleJourneyRef = framedVehicleJourneyRef

        estimatedVehicleJourney.dataSource = DATA_SOURCE

        val operatorRef = OperatorRefStructure()
        operatorRef.value = "$OPERATOR_PREFIX$airline"
        estimatedVehicleJourney.operatorRef = operatorRef

        addEstimatedCalls(estimatedVehicleJourney, flight, requestingAirportCode, scheduleTime)

        estimatedVehicleJourney.isIsCompleteStopSequence = true
        return estimatedVehicleJourney
    }

    private fun addEstimatedCalls(
        estimatedVehicleJourney: EstimatedVehicleJourney,
        flight: Flight,
        requestingAirportCode: String,
        scheduleTime: ZonedDateTime) {

        var estimatedCallsWrapper = estimatedVehicleJourney.getEstimatedCalls()
        if (estimatedCallsWrapper == null) {
            estimatedCallsWrapper = EstimatedVehicleJourney.EstimatedCalls()
            estimatedVehicleJourney.setEstimatedCalls(estimatedCallsWrapper)
        }

        val calls = estimatedCallsWrapper.getEstimatedCalls()

        // Use merged data if available, otherwise fall back to original fields
        val depAirport = flight.departureAirport ?: if (flight.isDeparture()) requestingAirportCode else flight.airport
        val arrAirport = flight.arrivalAirport ?: if (flight.isArrival()) requestingAirportCode else flight.airport

        val depScheduleTime = parseTimestamp(flight.scheduledDepartureTime)
            ?: if (flight.isDeparture()) scheduleTime else null
        val arrScheduleTime = parseTimestamp(flight.scheduledArrivalTime)
            ?: if (flight.isArrival()) scheduleTime else null

        val depStatus = flight.departureStatus ?: if (flight.isDeparture()) flight.status else null
        val arrStatus = flight.arrivalStatus ?: if (flight.isArrival()) flight.status else null

        // Create departure call
        if (depAirport != null) {
            val depStatusTime = parseTimestamp(depStatus?.time)
            val departureCall = createDepartureCall(
                depAirport,
                depScheduleTime,
                depStatus?.code,
                depStatusTime
            )
            calls.add(departureCall)
        }
        else {
            val originAirport = flight.airport
            if (originAirport != null) {
                val originCall = EstimatedCall()
                val originStopRef = StopPointRefStructure()
                originStopRef.value = findStopPointRef(originAirport)
                originCall.stopPointRef = originStopRef
                originCall.order = BigInteger.ONE
                calls.add(originCall)
            }
        }

        // Create arrival call
        if (arrAirport != null) {
            val arrStatusTime = parseTimestamp(arrStatus?.time)
            val arrivalCall = createArrivalCall(
                arrAirport,
                arrScheduleTime,
                arrStatus?.code,
                arrStatusTime,
                if (depAirport != null) BigInteger.valueOf(2) else BigInteger.ONE
            )
            calls.add(arrivalCall)
        }
    }

    private fun createDepartureCall(
        airportCode: String,
        scheduleTime: ZonedDateTime?,
        statusCode: String?,
        statusTime: ZonedDateTime?
    ): EstimatedCall {

        val call = EstimatedCall()

        val stopPointRef = StopPointRefStructure()
        stopPointRef.value = findStopPointRef(airportCode)
        call.stopPointRef = stopPointRef

        call.order = BigInteger.ONE

        if (scheduleTime != null) {
            call.aimedDepartureTime = scheduleTime

            when (statusCode) {
                FlightCodes.DEPARTED_CODE -> {
                    call.expectedDepartureTime = statusTime ?: scheduleTime
                    call.departureStatus = CallStatusEnumeration.MISSED
                }
                FlightCodes.NEW_TIME_CODE -> {
                    if (statusTime != null && statusTime == scheduleTime) {
                        call.expectedDepartureTime = scheduleTime
                        call.departureStatus = CallStatusEnumeration.ON_TIME
                    } else {
                        call.expectedDepartureTime = statusTime ?: scheduleTime
                        call.departureStatus = CallStatusEnumeration.DELAYED
                    }
                }
                FlightCodes.CANCELLED_CODE -> {
                    call.expectedDepartureTime = statusTime ?: scheduleTime
                    call.departureStatus = CallStatusEnumeration.CANCELLED
                    call.setCancellation(true)
                }
                else -> {
                    LOG.info("Unknow departure code {} for flight {}", statusCode, airportCode)
                    call.expectedDepartureTime = scheduleTime
                    call.departureStatus = CallStatusEnumeration.ON_TIME
                }
            }
        }
        return call
    }

    private fun createArrivalCall(
        airportCode: String,
        scheduleTime: ZonedDateTime?,
        statusCode: String?,
        statusTime: ZonedDateTime?,
        order: BigInteger
    ): EstimatedCall {
        val call = EstimatedCall()

        val stopPointRef = StopPointRefStructure()
        stopPointRef.value = findStopPointRef(airportCode)
        call.stopPointRef = stopPointRef

        call.order = order

        if (scheduleTime != null) {
            call.aimedArrivalTime = scheduleTime

            when (statusCode) {
                FlightCodes.ARRIVED_CODE -> {
                    call.expectedArrivalTime = statusTime ?: scheduleTime
                    call.arrivalStatus = CallStatusEnumeration.ARRIVED
                }
                FlightCodes.NEW_TIME_CODE -> {
                    if (statusTime != null && statusTime.isBefore(scheduleTime)) {
                        call.expectedArrivalTime = statusTime
                        call.arrivalStatus = CallStatusEnumeration.EARLY
                    } else if (statusTime != null && statusTime == scheduleTime) {
                        call.expectedArrivalTime = scheduleTime
                        call.arrivalStatus = CallStatusEnumeration.ON_TIME
                    } else {
                        call.expectedArrivalTime = statusTime ?: scheduleTime
                        call.arrivalStatus = CallStatusEnumeration.DELAYED
                    }
                }
                FlightCodes.CANCELLED_CODE -> {
                    call.arrivalStatus = CallStatusEnumeration.CANCELLED
                    call.setCancellation(true)
                }
                else -> {
                    LOG.info("Unknown arrival code {} for flight {}", statusCode, airportCode)
                    call.expectedArrivalTime = scheduleTime
                    call.arrivalStatus = CallStatusEnumeration.ON_TIME
                }
            }
        }
        return call
    }

    /**
     * Finds first, and for now only quay belonging to wanted airport, if no quay found returns IATA code
     * @param airportCode String, IATA code of airport used as a map key
     * @return String, First quay belonging to specified airport.
     */
    private fun findStopPointRef(airportCode: String): String {
        return airportQuayService.getQuayId(airportCode) ?: "$STOP_POINT_REF_PREFIX${airportCode}"
    }

    //TODO WHEN MULTI-LEG IS FULLY IMPLEMENTED MAKE SURE THE ROUTES STAY CORRECTED
    /**
     * Function that builds the routes used for LineRef made from the depature and arrival airports,
     * and DatedVehicleJourneyRef made out of fullRoute of departure, arrival and [viaAirports] list.
     * Can be ordered by size priority, if ordered by size it returns only departure and arrival meant for use in LineRef.
     * Full route with viaAirport cannot be ordered by size.
     *
     * @param requestingIATACode String. The airport IATA code used in the API call
     * @param flight Flight. The specified flight that is routed to. Provides airline, [departureAirport] [arrivingAirport] and [viaAirports] list.
     * @param wantOrdered Boolean. Specifies if you want the route ordered by size priority
     * @return String. Route code, "airline_depAirport-viaAirports-arrAirport" either ordered by largest first or requestingAirportCode. viaAirports only added if unordered.
     *
     */
     private fun routeBuilder(
        requestingIATACode: String,
        flight: Flight,
        wantOrdered: Boolean
     ): String {

        val airline = flight.airline

        // For merged flights, use departureAirport/arrivalAirport; otherwise use original fields
        val depAirport = flight.departureAirport
            ?: if (flight.isDeparture()) requestingIATACode else flight.airport
        val arrAirport = flight.arrivalAirport
            ?: if (flight.isArrival()) requestingIATACode else flight.airport

        val allAirports = if(!wantOrdered && flight.viaAirports.isNotEmpty()) {
            listOfNotNull(depAirport) + flight.viaAirports + listOfNotNull(arrAirport)
        } else {
            listOfNotNull(depAirport) + listOfNotNull(arrAirport)
        }

        val orderedAirports = if (wantOrdered) {
            orderAirportsBySize(allAirports)
        } else {
            allAirports
        }

        return "${airline}_${orderedAirports.joinToString("-")}"
    }

    /**
     * Extension function that generates a deterministic numeric ID hash from string.
     * Makes hash code using a variant of DJB2 algorithim also found in the EnTur repo ExTime.
     *
     * @param length Int. The max number of digits wanted in the returned hash string.
     * @return a numeric string of up to [length] digits dervied from hashing String.
     *
     */
    private fun String.idHash(length: Int): String {
        val hashcode = fold(0) { acc, char -> (acc shl 5) - acc + char.code }
        return abs(hashcode).toString().take(length)
    }

    // ── Chain-based multileg path ─────────────────────────────────────────────

    /**
     * Maps a list of UnifiedFlight chains into a SIRI EstimatedTimetableDelivery.
     * Used by the /siri endpoint after FlightAggregationService has stitched all flights.
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

        // LineRef uses size-ordered airports (same as existing mapFlightToEstimatedVehicleJourney)
        val ordered = orderAirportsBySize(listOf(flight.origin, flight.destination))
        val lineRef = LineRef()
        lineRef.value = "$LINE_PREFIX${flight.operator}_${ordered[0]}-${ordered[1]}"
        journey.lineRef = lineRef

        val operatorRef = OperatorRefStructure()
        operatorRef.value = "$OPERATOR_PREFIX${flight.operator}"
        journey.operatorRef = operatorRef

        val directionRef = DirectionRefStructure()
        // Circular routes (origin == destination) default to "outbound".
        // Otherwise: if the flight departs from the larger (hub) airport it is "outbound",
        // and from the smaller (regional) airport it is "inbound" — consistent with LineRef ordering.
        directionRef.value = if (flight.origin == flight.destination || flight.origin == ordered[0]) "outbound" else "inbound"
        journey.directionRef = directionRef

        val framedVehicleJourneyRef = FramedVehicleJourneyRefStructure()
        val dataFrameRef = DataFrameRefStructure()
        dataFrameRef.value = flight.date.toString()
        framedVehicleJourneyRef.dataFrameRef = dataFrameRef

        // VJR hash uses the full ordered route including intermediate stops (not size-sorted),
        // matching how ExTime and main's routeBuilder(wantOrdered=false) compute it
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
                    // Route hash check is skipped for the chain path: our stop sequence may
                    // differ from ExTime's route format for multi-leg/circular flights
                    // (e.g. TOS→HFT→SOJ→HFT→TOS stitched as 5 stops vs ExTime's 4-node format)
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
