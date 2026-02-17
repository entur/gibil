package org.gibil.util

import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for ZipHandling class using real files and temporary directories.
 */
class ZipHandlingIntegrationTest {

    private lateinit var zipHandling: ZipHandling

    @BeforeEach
    fun setup() {
        zipHandling = ZipHandling()
    }

    @Test
    fun `downloadAndUnzip should download and extract files successfully`(@TempDir tempDir: Path) {
        // Create a test zip file locally
        val zipPath = tempDir.resolve("test.zip")
        createTestZipFile(zipPath.toString())

        val outputDir = tempDir.resolve("output").toString()

        // local file URL with temporary zip file and directory
        val fileUrl = "file:///${zipPath.toString().replace("\\", "/")}"

        zipHandling.downloadAndUnzip(fileUrl, outputDir)

        // Verify extraction
        assertTrue(Files.exists(Paths.get(outputDir, "test-file.txt")))
    }

    @Test
    fun `should handle nested directories in zip file`(@TempDir tempDir: Path) {
        val zipPath = tempDir.resolve("nested.zip")
        createNestedZipFile(zipPath.toString())

        val outputDir = tempDir.resolve("output").toString()
        val fileUrl = "file:///${zipPath.toString().replace("\\", "/")}"

        zipHandling.downloadAndUnzip(fileUrl, outputDir)

        // Verify nested structure
        assertTrue(Files.exists(Paths.get(outputDir, "folder1", "folder2", "nested.txt")))
    }

    @Test
    fun `should handle malformed URL gracefully`(@TempDir tempDir: Path) {
        val outputDir = tempDir.resolve("output").toString()

        // Test malformed URL
        Assertions.assertThrows(ZipHandlingFailure::class.java) {
            zipHandling.downloadAndUnzip("not-a-valid-url", outputDir)
        }
    }

    @Test
    fun `should handle corrupted zip file gracefully`(@TempDir tempDir: Path) {
        val corruptedZip = tempDir.resolve("corrupted.zip")
        Files.write(corruptedZip, "This is not a valid zip file".toByteArray())

        val outputDir = tempDir.resolve("output").toString()
        val fileUrl = "file:///${corruptedZip.toString().replace("\\", "/")}"

        // Should handle error gracefully
        Assertions.assertThrows(ZipHandlingFailure::class.java) {
            zipHandling.downloadAndUnzip(fileUrl, outputDir)
        }
    }

    // Helper method to create a test ZIP file
    private fun createTestZipFile(zipPath: String) {
        ZipOutputStream(FileOutputStream(zipPath)).use { zos ->
            val entry = ZipEntry("test-file.txt")
            zos.putNextEntry(entry)
            zos.write("Test content".toByteArray())
            zos.closeEntry()
        }
    }

    private fun createNestedZipFile(zipPath: String) {
        ZipOutputStream(FileOutputStream(zipPath)).use { zos ->
            // Create nested directory structure
            val entry1 = ZipEntry("folder1/folder2/")
            zos.putNextEntry(entry1)
            zos.closeEntry()

            val entry2 = ZipEntry("folder1/folder2/nested.txt")
            zos.putNextEntry(entry2)
            zos.write("Nested content".toByteArray())
            zos.closeEntry()
        }
    }
}