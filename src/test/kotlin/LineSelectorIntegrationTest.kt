package org.gibil

import filter.LineSelector
import org.entur.netex.tools.cli.app.FilterNetexApp
import org.entur.netex.tools.lib.config.CliConfig
import org.entur.netex.tools.lib.config.FilterConfig
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Assertions.*
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LineSelectorIntegrationTest {
    val pathBase = "src/test/resources/extime/"
    val outputDir = File("$pathBase/lineSelectorTestOutput")
    val exampleFiles = listOf("AVI_AVI-Line-DY_OSL-BGO_Oslo-Bergen.xml", "AVI_AVI-Line-DY_OSL-TRD_Oslo-Trondheim.xml", "AVI_AVI-Line-SK_OSL-BGO_Oslo-Bergen.xml", "AVI_AVI-Line-SK_OSL-SVG_Oslo-Stavanger.xml", "AVI_AVI-Line-SK_OSL-TRD_Oslo-Trondheim.xml")


    @BeforeAll
    fun runFilteringProcess() {
        val filterConfig = FilterConfig(
            entitySelectors = listOf(LineSelector(setOf("AVI:Line:SK_OSL-SVG"))),
            preserveComments = true,
            pruneReferences = true,
            referencesToExcludeFromPruning = setOf(
                "DayType",
                "DayTypeAssignment",
                "DayTypeRef",
                "TimetabledPassingTime",
                "passingTimes",
                "StopPointInJourneyPatternRef"
            ),
            unreferencedEntitiesToPrune = setOf(
                "DayTypeAssignment",
                "JourneyPattern", "Route", "PointOnRoute", "DestinationDisplay"
            )
        )
        FilterNetexApp(
            cliConfig = CliConfig(alias = mapOf()),
            filterConfig = filterConfig,
            input = File("$pathBase/input"),
            target = outputDir
        ).run()

        println("=== Filtering process completed. ===")
    }

    @Test
    fun `Example files should exist after filtering`(){
        exampleFiles.forEach { fileName ->
            val outputFile = outputDir.resolve(fileName)
            assertTrue(outputFile.exists(), "Output file should exist")
        }
    }

    @Test
    fun `Example files should contain frame structure information`(){
        exampleFiles.forEach { fileName ->
            val outputFile = outputDir.resolve(fileName)
            val content = outputFile.readText()
            //check if all different structural elements are present in the output file
            assertTrue(content.contains("PublicationDelivery"), "Should preserve frame structure")
            assertTrue(content.contains("CompositeFrame"), "Should preserve frame structure")
            assertTrue(content.contains("ServiceFrame"), "Should preserve frame structure")
        }
    }

    @Test
    fun `Example file with matched lineid should contain servicejourneys`(){ "AVI:Line:SK_OSL-SVG"
        val outputFile = outputDir.resolve("AVI_AVI-Line-SK_OSL-SVG_Oslo-Stavanger.xml")
        val content = outputFile.readText()
        //check if all different structural elements are present in the output file
        assertTrue(content.contains("AVI:Line:SK_OSL-SVG"), "Should contain correct lineid")
        assertTrue(content.contains("ServiceJourney"), "Should contain servicejourney")
    }

    @Test
    fun `Example files should be valid XML`() {
        exampleFiles.forEach { fileName ->
            val outputFile = outputDir.resolve(fileName)

            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true

            val doc = factory.newDocumentBuilder().parse(outputFile)

            assertNotNull(doc, "Should be valid XML")
            assertEquals("PublicationDelivery", doc.documentElement.localName)
        }
    }
}
