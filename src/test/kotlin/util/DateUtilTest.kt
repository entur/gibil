package util

import org.junit.jupiter.api.*
import kotlin.test.Test
import util.DateUtil.formatForServiceJourney
import util.DateUtil.parseTime
import java.time.Instant

class DateUtilTest {
    //example good inputs
    val dateTimeZoneExample = "2026-05-05T06:00:00Z"
    val dateTimeExample = "2026-05-05T06:00:00"

    //example bad inputs
    val exampleNanDate = "imorgen klokka 12"

    //example expected result
    val parseTimestampExpectedResponse = Instant.parse(dateTimeZoneExample)

    @Test
    fun `FormatDateTimeZoneToTime should return correct formats`() {
        val formattedDates = formatForServiceJourney(dateTimeZoneExample)

        Assertions.assertTrue(formattedDates[0] == "08:00:00", "First returned item in list should be HH:mm:ss")
        Assertions.assertTrue(
            formattedDates[1] == "May_Tue_05",
            "Second returned item in list should be english MMM_E_dd format"
        )
    }

    @Test
    fun `FormatDateTimeZoneToTime should throw exception when invalid date format is given`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            formatForServiceJourney(exampleNanDate)
        }
    }

    @Test
    fun `ParseTimestamp should return correct timestamp`() {
        val response = parseTime(dateTimeZoneExample)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(parseTimestampExpectedResponse, response, "ParseTimestamp should return a datetimezone object when param is good")
    }

    @Test
    fun `ParseTimestamp should throw exception when parsing fails`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            parseTime(exampleNanDate)
        }
    }

    @Test
    fun `parseTime should correctly parse timestamp without timezone and default to UTC`() {
        val result = parseTime("2024-01-15T10:30:00")

        Assertions.assertEquals(Instant.parse("2024-01-15T10:30:00Z"), result)
    }

    @Test
    fun `ParseTimestamp should return null when input is null`() {
        val response = parseTime(null)

        Assertions.assertNull(response)
    }
}
