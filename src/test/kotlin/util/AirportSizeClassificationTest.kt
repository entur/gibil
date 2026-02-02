package util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AirportSizeClassificationTest {

    @Nested
    inner class GetSizePriority {

        @Test
        fun `should return priority 3 for large airport OSL`() {
            val result = AirportSizeClassification.getSizePriority("OSL")
            assertEquals(3, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["BGO", "BOO", "SVG", "TRD"])
        fun `should return priority 2 for medium airports`(airportCode: String) {
            val result = AirportSizeClassification.getSizePriority(airportCode)
            assertEquals(2, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["AES", "TOS", "HAU", "KRS", "MOL"])
        fun `should return priority 1 for small airports`(airportCode: String) {
            val result = AirportSizeClassification.getSizePriority(airportCode)
            assertEquals(1, result)
        }
    }

    @Nested
    inner class OrderAirportBySize {

        @Test
        fun `should return requesting airport first when it is larger`() {
            val result = AirportSizeClassification.orderAirportBySize("OSL", "BGO")

            assertEquals("OSL" to "BGO", result)
        }

        @Test
        fun `should return flight airport first when it is larger`() {
            val result = AirportSizeClassification.orderAirportBySize("BGO", "OSL")

            assertEquals("OSL" to "BGO", result)
        }

        @Test
        fun `should return requesting airport first when both are same size`() {
            val result = AirportSizeClassification.orderAirportBySize("BGO", "SVG")

            assertEquals("BGO" to "SVG", result)
        }

        @Test
        fun `should return requesting airport first when both are large`() {
            val result = AirportSizeClassification.orderAirportBySize("OSL", "OSL")

            assertEquals("OSL" to "OSL", result)
        }

        @Test
        fun `should return requesting airport first when both are small`() {
            val result = AirportSizeClassification.orderAirportBySize("AES", "TOS")

            assertEquals("AES" to "TOS", result)
        }

        @Test
        fun `should handle medium airport larger than small airport`() {
            val result = AirportSizeClassification.orderAirportBySize("AES", "BGO")

            assertEquals("BGO" to "AES", result)
        }

        @Test
        fun `should handle large airport with small airport`() {
            val result = AirportSizeClassification.orderAirportBySize("AES", "OSL")

            assertEquals("OSL" to "AES", result)
        }
    }
}