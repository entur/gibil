package siri

import model.xmlFeedApi.Airport
import model.xmlFeedApi.Flight
import model.xmlFeedApi.MultiLegFlight
import org.gibil.service.AirportQuayService
import uk.org.siri.siri21.*
import util.AirportSizeClassification.orderAirportsBySize
import java.math.BigInteger
import java.time.ZonedDateTime
import kotlin.math.abs
import service.FindServiceJourney
import org.gibil.Dates
import org.gibil.FlightCodes
import util.DateUtil.parseTimestamp
import org.gibil.SiriConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Component
class SiriETMapper(private val airportQuayService: AirportQuayService) {
private val LOG = LoggerFactory.getLogger(SiriETMapper::class.java)

@Service
class SiriETMapper(
    private val airportQuayService: AirportQuayService,
    private val findServiceJourney: FindServiceJourney
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
     * Maps both direct (merged) flights and multi-leg flights into a single SIRI document.
     * Direct flights produce a two-stop EstimatedVehicleJourney (departure + arrival).
     * Multi-leg flights produce an N+1 stop EstimatedVehicleJourney covering every airport in the journey.
     *
     * @param directFlights Collection of merged two-airport flights
     * @param multiLegFlights List of detected multi-leg flights
     * @return SIRI document with complete EstimatedCalls for all flights
     */
    fun mapAllFlightsToSiri(
        directFlights: Collection<Flight>,
        multiLegFlights: List<MultiLegFlight> = emptyList()
    ): Siri {
        val siri = Siri()

        val serviceDelivery = ServiceDelivery()
        serviceDelivery.responseTimestamp = ZonedDateTime.now()

        val producerRef = RequestorRef()
        producerRef.value = PRODUCER_REF
        serviceDelivery.producerRef = producerRef

        val delivery = EstimatedTimetableDeliveryStructure()
        delivery.version = SiriConfig.SIRI_VERSION_DELIVERY
        delivery.responseTimestamp = ZonedDateTime.now()

        val estimatedVersionFrame = EstimatedVersionFrameStructure()
        estimatedVersionFrame.recordedAtTime = ZonedDateTime.now()

        // Map direct (two-airport) flights
        directFlights.forEach { flight ->
            val contextAirport = flight.departureAirport ?: flight.arrivalAirport ?: return@forEach
            val evj = mapFlightToEstimatedVehicleJourney(flight, contextAirport)
            if (evj != null) {
                estimatedVersionFrame.estimatedVehicleJourneies.add(evj)
            }
        }

        // Map multi-leg flights
        multiLegFlights.forEach { mlf ->
            val evj = mapMultiLegFlightToEstimatedVehicleJourney(mlf)
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
                    findServiceJourney.matchServiceJourney(scheduledDepartureTime, flightId)

                //a match was found
                if (flightId in findFlightSequence && routeCodeId in findFlightSequence) {
                    //match was validated by routecode and flightId
                    framedVehicleJourneyRef.datedVehicleJourneyRef = findFlightSequence
                } else {
                    //match was not validated
                    framedVehicleJourneyRef.datedVehicleJourneyRef = "Couldn't validate VehicleJourneyRefID: $flightId = $findFlightSequence (${flightId in findFlightSequence}), $routeCodeId = $findFlightSequence (${routeCodeId in findFlightSequence})"

                    //log the failed match attempt
                    LOG.error("{}, {}, errors/{}", framedVehicleJourneyRef.datedVehicleJourneyRef, flightId, Dates.currentDateMMMddyyyy())
                }
            }
        } catch (e: Exception) {
            framedVehicleJourneyRef.datedVehicleJourneyRef = "ERROR finding VJR-ID or no match found $flightId: ${e.message}"

            LOG.error("Error finding VJR-ID for flightId {}: {}", flightId, e.message)

        }

        estimatedVehicleJourney.framedVehicleJourneyRef = framedVehicleJourneyRef

        estimatedVehicleJourney.dataSource = DATA_SOURCE
        estimatedVehicleJourney.isCancellation

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

    /**
     * Maps a multi-leg flight to an EstimatedVehicleJourney with N+1 estimated calls
     * (one per airport in the journey). Intermediate stops get both arrival and departure info.
     */
    private fun mapMultiLegFlightToEstimatedVehicleJourney(
        multiLegFlight: MultiLegFlight
    ): EstimatedVehicleJourney? {

        val firstLeg = multiLegFlight.legs.firstOrNull() ?: return null
        val scheduleTime = parseTimestamp(firstLeg.scheduledDepartureTime) ?: return null

        val routeCode = "${multiLegFlight.airline}_${multiLegFlight.originAirport}-${multiLegFlight.destinationAirport}"
        val routeCodeId = routeCode.idHash(10)

        val evj = EstimatedVehicleJourney()

        val lineRef = LineRef()
        lineRef.value = "$LINE_PREFIX$routeCode"
        evj.lineRef = lineRef

        val directionRef = DirectionRefStructure()
        directionRef.value = "outbound"
        evj.directionRef = directionRef

        val framedVehicleJourneyRef = FramedVehicleJourneyRefStructure()
        val dataFrameRef = DataFrameRefStructure()
        dataFrameRef.value = scheduleTime.toLocalDate().toString()
        framedVehicleJourneyRef.dataFrameRef = dataFrameRef
        framedVehicleJourneyRef.datedVehicleJourneyRef = "${VEHICLE_JOURNEY_PREFIX}${multiLegFlight.flightId}-01-${routeCodeId}"
        evj.framedVehicleJourneyRef = framedVehicleJourneyRef

        evj.dataSource = DATA_SOURCE

        val operatorRef = OperatorRefStructure()
        operatorRef.value = "$OPERATOR_PREFIX${multiLegFlight.airline}"
        evj.operatorRef = operatorRef

        // Build estimated calls: one per airport in the journey
        val callsWrapper = EstimatedVehicleJourney.EstimatedCalls()
        val calls = callsWrapper.getEstimatedCalls()
        val allStops = multiLegFlight.allStops

        allStops.forEachIndexed { index, airportCode ->
            val arrLeg = if (index > 0) multiLegFlight.legs[index - 1] else null
            val depLeg = if (index < multiLegFlight.legs.size) multiLegFlight.legs[index] else null
            val order = BigInteger.valueOf((index + 1).toLong())

            val call = EstimatedCall()
            val stopPointRef = StopPointRefStructure()
            stopPointRef.value = findStopPointRef(airportCode)
            call.stopPointRef = stopPointRef
            call.order = order

            // Add departure info (origin and intermediate stops)
            if (depLeg != null) {
                val depSchedule = parseTimestamp(depLeg.scheduledDepartureTime)
                val depStatusTime = parseTimestamp(depLeg.expectedDepartureTime)
                addDepartureStatus(call, depLeg.departureStatus?.code, depStatusTime, depSchedule)
            }

            // Add arrival info (intermediate and destination stops)
            if (arrLeg != null) {
                val arrSchedule = parseTimestamp(arrLeg.scheduledArrivalTime)
                val arrStatusTime = parseTimestamp(arrLeg.expectedArrivalTime)
                addArrivalStatus(call, arrLeg.arrivalStatus?.code, arrStatusTime, arrSchedule)
            }
            calls.add(call)
        }

        evj.setEstimatedCalls(callsWrapper)
        evj.isIsCompleteStopSequence = true
        return evj
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

        addDepartureStatus(call, statusCode, statusTime, scheduleTime)
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

        addArrivalStatus(call, statusCode, statusTime, scheduleTime)

        return call
    }

    private fun parseTimestamp(timestamp: String?): ZonedDateTime? {
        if (timestamp.isNullOrBlank()) return null

        return try {
            ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME)
        } catch (_: DateTimeParseException) {
            try {
                // Try parsing without timezone (assume UTC)
                java.time.LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(java.time.ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                println("Warning: Could not parse timestamp: $timestamp")
                null
            }
        }
    }

    //Helper functions to set arrival and departure status on calls based on status codes and times from the API.
    private fun addArrivalStatus(call: EstimatedCall, statusCode: String?, statusTime: ZonedDateTime?, scheduleTime: ZonedDateTime?) {
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
                    call.expectedArrivalTime = statusTime ?: scheduleTime
                    call.arrivalStatus = CallStatusEnumeration.CANCELLED
                    call.setCancellation(true)
                }
                else -> {
                    call.expectedArrivalTime = scheduleTime
                    call.arrivalStatus = CallStatusEnumeration.ON_TIME
                }
            }
        }
    }

    private fun addDepartureStatus(call: EstimatedCall, statusCode: String?, statusTime: ZonedDateTime?, scheduleTime: ZonedDateTime?) {
        if (scheduleTime != null) {
            call.aimedDepartureTime = scheduleTime

            when (statusCode) {
                "D" -> {
                    call.expectedDepartureTime = statusTime ?: scheduleTime
                    call.departureStatus = CallStatusEnumeration.MISSED
                }
                "E" -> {
                    if (statusTime != null && statusTime == scheduleTime) {
                        call.expectedDepartureTime = scheduleTime
                        call.departureStatus = CallStatusEnumeration.ON_TIME
                    } else {
                        call.expectedDepartureTime = statusTime ?: scheduleTime
                        call.departureStatus = CallStatusEnumeration.DELAYED
                    }
                }
                "C" -> {
                    call.expectedDepartureTime = statusTime ?: scheduleTime
                    call.departureStatus = CallStatusEnumeration.CANCELLED
                    call.setCancellation(true)
                }
                else -> {
                    call.expectedDepartureTime = scheduleTime
                    call.departureStatus = CallStatusEnumeration.ON_TIME
                }
            }
        }
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
}