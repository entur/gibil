package util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.gibil.util.Dates
import org.gibil.util.Dates.daytypeBuilder

/**
 * Utility object for date handling throughout the project.
 *
 * Some of the functions are very specific, while others are more applicable other places
 */
object DateUtil {

    /**
     * Parses an ISO-8601 timestamp string into a [ZonedDateTime].
     *
     * First attempts to parse the timestamp with timezone info (e.g. "2024-01-15T10:30:00+02:00").
     * If that fails, attempts to parse it as a local date-time without timezone (e.g. "2024-01-15T10:30:00"),
     * assuming UTC in that case.
     *
     * @param timestamp The ISO-8601 timestamp string to parse, or null/blank.
     * @return A [ZonedDateTime] representing the parsed timestamp, or null if the input is null, blank, or unparseable.
     */
    fun parseTimestamp(timestamp: String?): ZonedDateTime? {
        if (timestamp.isNullOrBlank()) return null

        return try {
            ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: DateTimeParseException) {
            try {
                // Try parsing without timezone (assume UTC)
                java.time.LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(java.time.ZoneOffset.UTC)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException(
                    "Could not parse timestamp: '$timestamp'. " +
                            "Expected ISO-8601 format with timezone (e.g. '2024-01-15T10:30:00+02:00') " +
                            "or without (e.g. '2024-01-15T10:30:00').",
                    e  // preserve the original cause
                )
            }
        }
    }

    fun parseTime(timeStr: String?): Instant? {
        if(timeStr.isNullOrBlank()) return null
        return parseTimestamp(timeStr)?.toInstant()
    }

    /**
     * Formatting for making a time object into a list containing a departure time and a daytyperef based on the departuretime given.
     * This method is specifically made to be used for matching flight information to a netex flight.
     * @param departureTimeString A String representing the departuretime
     * @return a list containing two objects; departuretime in norwegian timezone, a DayTypeRef string (MMM_E_dd)
     */
    fun formatForServiceJourney(departureTimeString: String): List<String> {
        try {
            //parse parameter into a ZonedDateTime object
            val dateTimeDepartureWithZone  = ZonedDateTime.parse(departureTimeString)

            // Norwegian timezone
            val norwayZone = ZoneId.of("Europe/Oslo")

            // Convert to Norwegian timezone
            val norwayDateTimeDeparture = dateTimeDepartureWithZone .withZoneSameInstant(norwayZone)

            val dayType = daytypeBuilder(norwayDateTimeDeparture)

            // different formats needed, with locale to ensure month and day names are in English, as the day type references in the service journeys are in English
            val formatFull = DateTimeFormatter.ofPattern("HH:mm:ss", Dates.LOCALE)

            val norwegianDepartureTime = norwayDateTimeDeparture.format(formatFull)

            return listOf(norwegianDepartureTime, dayType)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid date-time format: $departureTimeString. Expected format: ISO 8601 (e.g., 2026-02-07T13:40:00Z)", e ) }
    }

    /**
     * Converts nanoseconds to milliseconds
     * @param nanos Nanoseconds to be converted to milliseconds
     * 1_000_000.0 is used to convert nanoseconds to milliseconds.
     * Since System.nanoTime() returns nanoseconds, dividing by 1,000,000 gives you milliseconds.
     * The .0 makes it a Double so you get a decimal result (e.g. 2.35 ms) rather than a truncated integer.
     * @return Milliseconds in Double format
    */
    fun nanosToMs(nanos: Long): Double = nanos / 1_000_000.0
}

