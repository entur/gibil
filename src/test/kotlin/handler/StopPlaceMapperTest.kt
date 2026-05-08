package handler

import org.gibil.handler.StopPlaceMapper
import org.gibil.util.QuayCodes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StopPlaceMapperTest {

    private lateinit var stopPlaceMapper: StopPlaceMapper

    @BeforeEach
    fun init() {
        stopPlaceMapper = StopPlaceMapper()
    }

    private fun defaultQuayXml(quayId: String, iata: String) = """
        <Quay id="$quayId">
            <keyList><KeyValue><Key>imported-id</Key><Value>AVI:Quay:$iata</Value></KeyValue></keyList>
        </Quay>"""

    private fun gateQuayXml(quayId: String, publicCode: String) = """
        <Quay id="$quayId">
            <keyList><KeyValue><Key>imported-id</Key><Value></Value></KeyValue></keyList>
            <PublicCode>$publicCode</PublicCode>
        </Quay>"""

    private fun blankPublicCodeQuayXml(quayId: String) = """
        <Quay id="$quayId">
            <keyList><KeyValue><Key>imported-id</Key><Value></Value></KeyValue></keyList>
            <PublicCode></PublicCode>
        </Quay>"""

    private fun buildXml(vararg quayXmls: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <PublicationDelivery xmlns="http://www.netex.org.uk/netex">
            <stopPlaces>
                <StopPlace>
                    <quays>
                        ${quayXmls.joinToString("\n")}
                    </quays>
                </StopPlace>
            </stopPlaces>
        </PublicationDelivery>""".trimIndent()

    @Nested
    inner class ParseStopPlaceFromFile {

        @Test
        fun `returns StopPlaces when file contains stopPlaces element`(@TempDir tempDir: Path) {
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <PublicationDelivery xmlns="http://www.netex.org.uk/netex">
                    <stopPlaces>
                        <StopPlace>
                            <StopPlaceType>airport</StopPlaceType>
                        </StopPlace>
                    </stopPlaces>
                </PublicationDelivery>
            """.trimIndent()
            val file = tempDir.resolve("test.xml").toFile().also { it.writeText(xml) }

            val result = stopPlaceMapper.parseStopPlaceFromFile(file)
            Assertions.assertEquals(1, result.stopPlace.size)
        }

        @Test
        fun `throws RuntimeException when stopPlaces element is missing`(@TempDir tempDir: Path) {
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <PublicationDelivery xmlns="http://www.netex.org.uk/netex">
                </PublicationDelivery>
            """.trimIndent()
            val file = tempDir.resolve("test.xml").toFile().also { it.writeText(xml) }

            val exception = assertThrows<RuntimeException> {
                stopPlaceMapper.parseStopPlaceFromFile(file)
            }
            Assertions.assertEquals("No <stopPlaces> found in ${file.name}", exception.message)
        }

        @Test
        fun `throws exception when file does not exist`(@TempDir tempDir: Path) {
            val file = tempDir.resolve("nonexistent.xml").toFile()

            assertThrows<Exception> {
                stopPlaceMapper.parseStopPlaceFromFile(file)
            }
        }
    }

    @Nested
    inner class MakeIataToQuayMap {

        @Test
        fun `maps default quay by IATA code`(@TempDir tempDir: Path) {
            val file = tempDir.resolve("test.xml").toFile()
                .also { it.writeText(buildXml(defaultQuayXml("NSR:Quay:1213", "BGO"))) }

            val result = stopPlaceMapper.makeIataToQuayMap(stopPlaceMapper.parseStopPlaceFromFile(file))

            assertEquals("NSR:Quay:1213", result["BGO"]?.get(QuayCodes.DEFAULT_KEY))
        }

        @Test
        fun `maps gate quays by PublicCode alongside default`(@TempDir tempDir: Path) {
            val file = tempDir.resolve("test.xml").toFile().also {
                it.writeText(buildXml(
                    defaultQuayXml("NSR:Quay:1213", "BGO"),
                    gateQuayXml("NSR:Quay:111610", "B16"),
                    gateQuayXml("NSR:Quay:111584", "C35")
                ))
            }

            val result = stopPlaceMapper.makeIataToQuayMap(stopPlaceMapper.parseStopPlaceFromFile(file))

            assertEquals("NSR:Quay:1213", result["BGO"]?.get(QuayCodes.DEFAULT_KEY))
            assertEquals("NSR:Quay:111610", result["BGO"]?.get("B16"))
            assertEquals("NSR:Quay:111584", result["BGO"]?.get("C35"))
        }

        @Test
        fun `skips stop place with no default quay`(@TempDir tempDir: Path) {
            val file = tempDir.resolve("test.xml").toFile()
                .also { it.writeText(buildXml(gateQuayXml("NSR:Quay:111610", "B16"))) }

            val result = stopPlaceMapper.makeIataToQuayMap(stopPlaceMapper.parseStopPlaceFromFile(file))

            assertTrue(result.isEmpty())
        }

        @Test
        fun `skips gate quay with blank PublicCode`(@TempDir tempDir: Path) {
            val file = tempDir.resolve("test.xml").toFile().also {
                it.writeText(buildXml(
                    defaultQuayXml("NSR:Quay:1213", "BGO"),
                    blankPublicCodeQuayXml("NSR:Quay:99999")
                ))
            }

            val result = stopPlaceMapper.makeIataToQuayMap(stopPlaceMapper.parseStopPlaceFromFile(file))

            assertEquals(1, result["BGO"]?.size)
            assertNull(result["BGO"]?.get(""))
        }
    }
}
