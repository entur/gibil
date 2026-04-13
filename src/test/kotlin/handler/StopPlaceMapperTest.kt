package handler

import org.gibil.handler.StopPlaceMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class StopPlaceMapperTest {

    private lateinit var stopPlaceMapper: StopPlaceMapper

    @BeforeEach
    fun init() {
        stopPlaceMapper = StopPlaceMapper()
    }

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
            val file = tempDir.resolve("test.xml").toFile()
            file.writeText(xml)

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
            val file = tempDir.resolve("test.xml").toFile()
            file.writeText(xml)

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
}
