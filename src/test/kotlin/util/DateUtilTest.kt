package util

import org.junit.jupiter.api.*
import kotlin.test.Test
import util.DateUtil.formatDateTimeZoneToTime
import util.DateUtil.parseTimestamp
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Integration tests for ZipUtil using real files and temporary directories.
 */
class DateUtilTest {
    //example good inputs
    val dateTimeZoneExample = "2026-05-05T06:00:00Z"

    //example bad inputs
    val exampleNanDate = "imorgen klokka 12"

    //example expected result
    val parseTimestampExpectedResponse = ZonedDateTime.parse(dateTimeZoneExample, DateTimeFormatter.ISO_DATE_TIME)


    @Test
    fun `FormatDateTimeZoneToTime should return correct formats`() {
        val formattedDates = formatDateTimeZoneToTime(dateTimeZoneExample)

        Assertions.assertTrue(formattedDates[0] == "08:00:00", "First returned item in list should be HH:mm:ss")
        Assertions.assertTrue(
            formattedDates[1] == "May_Tue_05",
            "Second returned item in list should be english MMM_E_dd format"
        )
    }

    @Test
    fun `FormatDateTimeZoneToTime should throw exception when invalid date format is given`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            formatDateTimeZoneToTime(exampleNanDate)
        }
    }

    @Test
    fun `ParseTimestamp should return correct timestamp`() {
        val response = parseTimestamp(dateTimeZoneExample)

        Assertions.assertNotNull(response)
        Assertions.assertEquals(parseTimestampExpectedResponse, response, "ParseTimestamp should return a datetimezone object when param is good")
    }

    @Test
    fun `ParseTimestamp should throw exception when parsing fails`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            parseTimestamp(exampleNanDate)
        }
    }
}
