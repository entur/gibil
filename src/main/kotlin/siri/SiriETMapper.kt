package siri

import util.AirportSizeClassification.orderAirportBySize
import model.avinorApi.Airport
import model.avinorApi.Flight
import uk.org.siri.siri21.*
import java.math.BigInteger
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs


class SiriETMapper {
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
    fun mapToSiri(airport: Airport, requestingAirportCode: String): Siri {
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

    private fun createEstimatedTimetableDelivery(
        airport: Airport, requestingAirportCode: String): EstimatedTimetableDeliveryStructure {
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
        flight: Flight, requestingAirportCode: String): EstimatedVehicleJourney? {

        // Skip flights without a flightId
        if (flight.flightId == null) return null
        val airline = flight.airline ?: return null
        val scheduleTime = parseTimestamp(flight.scheduleTime) ?: return null

        val estimatedVehicleJourney = EstimatedVehicleJourney()

        //Set lineRef
        val lineRef = LineRef()
        val route = routeBuilder(requestingAirportCode, flight)
        lineRef.value = "$LINE_PREFIX$route"
        estimatedVehicleJourney.lineRef = lineRef

        //Set directionRef
        val directionRef = DirectionRefStructure()
        directionRef.value = if (flight.isDeparture()) "outbound" else "inbound"
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
        return estimatedVehicleJourney
    }

    private fun addEstimatedCalls(estimatedVehicleJourney: EstimatedVehicleJourney, flight: Flight
    , requestingAirportCode: String, scheduleTime: ZonedDateTime) {
        val statusTime = parseTimestamp(flight.status?.time)


        var estimatedCallsWrapper = estimatedVehicleJourney.getEstimatedCalls()
        if (estimatedCallsWrapper == null) {
            estimatedCallsWrapper = EstimatedVehicleJourney.EstimatedCalls()
            estimatedVehicleJourney.setEstimatedCalls(estimatedCallsWrapper)
        }

        val calls = estimatedCallsWrapper.getEstimatedCalls()

        if (flight.isDeparture()) {
            val call = createDepartureCall(requestingAirportCode, scheduleTime,
                flight.status?.code, statusTime)
            calls.add(call)

            val destAirport = flight.airport
            if (destAirport != null) {
                val destCall = EstimatedCall()
                val destStopRef = StopPointRefStructure()
                //TODO! Will have to be changed when airport quays are expanded. (for now just use prefix + dest airport code)
                destStopRef.value = "$STOP_POINT_REF_PREFIX$destAirport"
                destCall.stopPointRef = destStopRef
                destCall.order = BigInteger.valueOf(2)
                calls.add(destCall)
            }
        }
        else {
            val originAirport = flight.airport
            if (originAirport != null) {
                val originCall = EstimatedCall()
                val originStopRef = StopPointRefStructure()
                originStopRef.value = "$STOP_POINT_REF_PREFIX$originAirport"
                originCall.stopPointRef = originStopRef
                originCall.order = BigInteger.ONE
                calls.add(originCall)
            }

            val destCall = createArrivalCall(requestingAirportCode, scheduleTime,
                flight.status?.code, statusTime,
                if (originAirport != null) BigInteger.valueOf(2) else BigInteger.ONE
            )
            calls.add(destCall)
        }
    }

    private fun createDepartureCall(airportCode: String, scheduleTime: ZonedDateTime,
                                    statusCode: String?, statusTime: ZonedDateTime?): EstimatedCall {
        val call = EstimatedCall()

        val stopPointRef = StopPointRefStructure()
        stopPointRef.value = "$STOP_POINT_REF_PREFIX$airportCode"
        call.stopPointRef = stopPointRef

        call.order = BigInteger.ONE
        call.aimedDepartureTime = scheduleTime

        when (statusCode) {
            "D" -> {
                call.expectedDepartureTime = statusTime ?: scheduleTime
                call.departureStatus = CallStatusEnumeration.DEPARTED
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
        return call
    }

    private fun createArrivalCall(
        airportCode: String,
        scheduleTime: ZonedDateTime,
        statusCode: String?,
        statusTime: ZonedDateTime?,
        order: BigInteger
    ): EstimatedCall {
        val call = EstimatedCall()

        val stopPointRef = StopPointRefStructure()
        stopPointRef.value = "$STOP_POINT_REF_PREFIX$airportCode"
        call.stopPointRef = stopPointRef

        call.order = order
        call.aimedArrivalTime = scheduleTime

        when (statusCode) {
            "A" -> {
                call.expectedArrivalTime = statusTime ?: scheduleTime
                call.arrivalStatus = CallStatusEnumeration.ARRIVED
            }
            "E" -> {
                call.expectedArrivalTime = statusTime ?: scheduleTime
                call.arrivalStatus = CallStatusEnumeration.DELAYED
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

     private fun routeBuilder(requestingAirportCode: String, flight: Flight, wantOrdered: Boolean = false): String {
        val airline = flight.airline

        val(firstAirport, secondAirport) = if(wantOrdered) {
            orderAirportBySize(requestingAirportCode, flight.airport.toString())
        } else {
            requestingAirportCode to flight.airport.toString()
        }

        return "${airline}_${firstAirport}-${secondAirport}"
    }

    private fun String.idHash(length: Int): String {
        val hashcode = fold(0) { acc, char -> (acc shl 5) - acc + char.code }
        return abs(hashcode).toString().take(length)
    }

}