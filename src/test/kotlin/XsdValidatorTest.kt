package siri.validator

import org.entur.siri.validator.SiriValidator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class XsdValidatorTest {

    private val validator = XsdValidator()

    private fun loadResource(path: String): String =
        this::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()
            ?: throw IllegalArgumentException("Resource not found: $path")


    @Test
    fun `validate valid SIRI-ET XML for SIRI version 2_0 with external xml file with realistic payload`() {
        val validXml = loadResource("/siri-example-data.xml")

        val result = validator.validateSirixml(validXml, SiriValidator.Version.VERSION_2_1)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate valid SIRI-ET XML for SIRI version 2_1`() {
        val validXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns5="http://www.opengis.net/gml/3.2">
    <ServiceDelivery>
        <ResponseTimestamp>2026-01-16T11:39:11.7066459+01:00</ResponseTimestamp>
        <ProducerRef>AVINOR</ProducerRef>
        <EstimatedTimetableDelivery version="2.1">
            <ResponseTimestamp>2026-01-16T11:39:11.7066459+01:00</ResponseTimestamp>
            <EstimatedJourneyVersionFrame>
                <RecordedAtTime>2026-01-16T10:39:06.241609Z</RecordedAtTime>
                <EstimatedVehicleJourney>
                    <LineRef>AVINOR:Line:WF</LineRef>
                    <DirectionRef>outbound</DirectionRef>
                    <FramedVehicleJourneyRef>
                        <DataFrameRef>2026-01-16</DataFrameRef>
                        <DatedVehicleJourneyRef>AVINOR:ServiceJourney:1209143907</DatedVehicleJourneyRef>
                    </FramedVehicleJourneyRef>
                    <OperatorRef>AVINOR:Operator:WF</OperatorRef>
                    <DataSource>AVINOR</DataSource>
                    <EstimatedCalls>
                        <EstimatedCall>
                            <StopPointRef>AVINOR:StopPlace:BGO</StopPointRef>
                            <Order>1</Order>
                            <AimedDepartureTime>2026-01-16T06:40:00Z</AimedDepartureTime>
                            <ExpectedDepartureTime>2026-01-16T08:01:00Z</ExpectedDepartureTime>
                            <DepartureStatus>departed</DepartureStatus>
                        </EstimatedCall>
                        <EstimatedCall>
                            <StopPointRef>AVINOR:StopPlace:TOS</StopPointRef>
                            <Order>2</Order>
                        </EstimatedCall>
                    </EstimatedCalls>
                </EstimatedVehicleJourney>
            </EstimatedJourneyVersionFrame>
        </EstimatedTimetableDelivery>
    </ServiceDelivery>
</Siri>
            """.trimIndent()

        val result = validator.validateSirixml(validXml, SiriValidator.Version.VERSION_2_1)

        assertTrue(result.isValid)
    }

    @Test
    fun `validate invalid SIRI-ET XML for SIRI version 2_1`() {
        val invalidXml = """Hello, not valid SIRI-ET!"""

        val result = validator.validateSirixml(invalidXml, SiriValidator.Version.VERSION_2_1)

        assertFalse(result.isValid)
    }

}


