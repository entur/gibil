package org.example

import handler.AvinorScheduleXmlHandler
import model.avinorApi.Airport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertNotNull

class AvinorScheduleXmlHandlerTest {

    private lateinit var handler: AvinorScheduleXmlHandler

    @BeforeEach
    fun init() {
        handler = AvinorScheduleXmlHandler()
    }

    @Test
    fun `unmarshallXmlToAirport should parse valid XML into Airport object`() {
        val validXml = """
        <airport name="OSL">
            <flights lastUpdate="2026-01-07T09:24:27.016611Z">
                <flight uniqueID="1494483548">
                    <airline>BT</airline>
                    <flight_id>BT152</flight_id>
                    <dom_int>S</dom_int>
                    <schedule_time>2026-01-07T07:30:00Z</schedule_time>
                    <arr_dep>D</arr_dep>
                    <airport>RIX</airport>
                    <check_in>1 2</check_in>
                    <gate>E8</gate>
                    <status code="D" time="2026-01-07T08:18:23Z"/>
                    <delayed>Y</delayed>
                </flight>
            </flights>
        </airport>
        """.trimIndent()

        val airport = handler.unmarshallXmlToAirport(validXml)
        assertNotNull(airport)
    }

    @Test
    fun `unmarshallXmlToAirport should throw exception for invalid XML`() {
        val invalidXml = """
        <airport name="OSL">
                <>
        """.trimIndent()

        val exception = assertThrows<RuntimeException> {
            handler.unmarshallXmlToAirport(invalidXml)
        }

        assertTrue(exception.message?.contains("Error parsing Airport") == true)
    }

    @Test
    fun `unmarshallXmlToAirport should throw exception for empty string`() {
        val invalidXml = ""

        val exception = assertThrows<RuntimeException> {
            handler.unmarshallXmlToAirport(invalidXml)
        }
        assertTrue(exception.message?.contains("Error parsing Airport") == true)
    }

    @Test
    fun `marshallAirport should convert Airport object to XML string`() {

        val airport = Airport("OSL")
        val xml = handler.marshallAirport(airport)

        assertNotNull(xml)
        assertTrue(xml.contains("<?xml"))
        assertTrue(xml.contains("airport"))
    }

    @Test
    fun `marshallAirport should produce formatted XML output`() {

        val airport = Airport("OSL")
        val xml = handler.marshallAirport(airport)

        assertTrue(xml.contains("\n"))
    }


}