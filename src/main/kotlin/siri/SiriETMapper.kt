package siri

import util.AirportSizeClassification.orderAirportBySize
import model.xmlFeedApi.Airport
import model.xmlFeedApi.Flight
import org.gibil.service.AirportQuayService
import org.springframework.stereotype.Component
import uk.org.siri.siri21.*
import java.math.BigInteger
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs


@Component
class SiriETMapper(private val airportQuayService: AirportQuayService) {
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
     * Maps a collection of merged flights (from all airports) to a SIRI document.
     * Use this when you have pre-aggregated flight data with complete departure/arrival info.
     * @param mergedFlights Collection of flights that have been merged across airports
     * @return SIRI document with complete EstimatedCalls for all flights
     */
    fun mapMergedFlightsToSiri(mergedFlights: Collection<Flight>): Siri {
        val siri = Siri()

        val serviceDelivery = ServiceDelivery()
        serviceDelivery.responseTimestamp = ZonedDateTime.now()

        val producerRef = RequestorRef()
        producerRef.value = PRODUCER_REF
        serviceDelivery.producerRef = producerRef

        val delivery = EstimatedTimetableDeliveryStructure()
        delivery.version = "2.1"
        delivery.responseTimestamp = ZonedDateTime.now()

        val estimatedVersionFrame = EstimatedVersionFrameStructure()
        estimatedVersionFrame.recordedAtTime = ZonedDateTime.now()

        mergedFlights.forEach { flight ->
            // For merged flights, use departureAirport as the "requesting" airport context
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
        delivery.version = "2.1"
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
        val route = routeBuilder(requestingAirportCode, flight)
        lineRef.value = "$LINE_PREFIX$route"
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

        val orderedRoute = routeBuilder(requestingAirportCode, flight, true)
        val routeCodeId = orderedRoute.idHash(10)

        //TODO! flightSequence is hardcoded "-01-" for testing. Needs to follow timetable version in extime
        // The sequence comes from a hash map and is difficult to replicate
        framedVehicleJourneyRef.datedVehicleJourneyRef = "${VEHICLE_JOURNEY_PREFIX}${flight.flightId}-01-${routeCodeId}"
        estimatedVehicleJourney.framedVehicleJourneyRef = framedVehicleJourneyRef

        estimatedVehicleJourney.dataSource = DATA_SOURCE
        //TODO! Find out what to do with ExtraJourney
        //estimatedVehicleJourney.extraJourney(false)
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
                "D" -> {
                    call.expectedDepartureTime = statusTime ?: scheduleTime
                    call.departureStatus = CallStatusEnumeration.MISSED
                }
                "E" -> {
                    call.expectedDepartureTime = statusTime ?: scheduleTime
                    call.departureStatus = CallStatusEnumeration.DELAYED
                }
                "C" -> {
                    call.departureStatus = CallStatusEnumeration.CANCELLED
                    call.setCancellation(true)
                }
                else -> {
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
                "A" -> {
                    call.expectedArrivalTime = statusTime ?: scheduleTime
                    call.arrivalStatus = CallStatusEnumeration.ARRIVED
                }
                "E" -> {
                    if (statusTime != null && statusTime.isBefore(scheduleTime)) {
                        call.expectedArrivalTime = statusTime
                        call.arrivalStatus = CallStatusEnumeration.EARLY
                    } else {
                        call.expectedArrivalTime = statusTime ?: scheduleTime
                        call.arrivalStatus = CallStatusEnumeration.DELAYED
                    }
                }
                "C" -> {
                    call.arrivalStatus = CallStatusEnumeration.CANCELLED
                    call.setCancellation(true)
                }
                else -> {
                    call.expectedArrivalTime = scheduleTime
                    call.arrivalStatus = CallStatusEnumeration.ON_TIME
                }
            }
        }
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

    /**
     * Finds first, and for now only quay belonging to wanted airport, if no quay found returns IATA code
     * @param airportCode String, IATA code of airport used as a map key
     * @return String, First quay belonging to specified airport.
     */
    private fun findStopPointRef(airportCode: String): String {
        return airportQuayService.getQuayId(airportCode) ?: "$STOP_POINT_REF_PREFIX${airportCode}"
    }

    /**
     * Function that builds the routes used for LineRef and DatedVehicleJourneyRef
     * Can be ordered by size priority or not depending on usecase
     *
     * @param requestingAirportCode String. The airport code used in the API call
     * @param flight Flight. The specified flight that is routed to. Provides airline and depature/arriving airport
     * @param wantOrdered Boolean. Specifies if you want the route ordered by size priority, default value false
     * @return String. Route code, "airline_firstAirport-SecondAirport" either ordered by largest first or requestingAirportCode first depends on param choice.
     *
     */
     private fun routeBuilder(
        requestingAirportCode: String,
        flight: Flight,
        wantOrdered: Boolean = true
     ): String {

        val airline = flight.airline

        // For merged flights, use departureAirport/arrivalAirport; otherwise use original fields
        val depAirport = flight.departureAirport
            ?: if (flight.isDeparture()) requestingAirportCode else flight.airport
        val arrAirport = flight.arrivalAirport
            ?: if (flight.isArrival()) requestingAirportCode else flight.airport

        val(firstAirport, secondAirport) = if(wantOrdered) {
            orderAirportBySize(depAirport.toString(), arrAirport.toString())
        } else {
            depAirport.toString() to arrAirport.toString()
        }

        return "${airline}_${firstAirport}-${secondAirport}"
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