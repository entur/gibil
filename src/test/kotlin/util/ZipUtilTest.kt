package util

import io.mockk.every
import io.mockk.mockk
import org.gibil.service.ApiService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for ZipUtil using real files and temporary directories.
 */
class ZipUtilTest {

    private val apiService = mockk<ApiService>()

    @Test
    fun `downloadAndUnzip should download and extract files successfully`(@TempDir tempDir: Path) {
        val zipPath = tempDir.resolve("test.zip")
        createTestZipFile(zipPath.toString())

        val outputDir = tempDir.resolve("output").toString()

        every { apiService.apiCallToFile(any(), any(), any()) } answers {
            val targetFile = secondArg<File>()
            zipPath.toFile().copyTo(targetFile, overwrite = true)
        }

        ZipUtil.downloadAndUnzip("https://example.com/test.zip", outputDir, apiService)

        assertTrue(Files.exists(Paths.get(outputDir, "test-file.txt")))
    }

    @Test
    fun `downloadAndUnzip should handle nested directories in zip file`(@TempDir tempDir: Path) {
        val zipPath = tempDir.resolve("nested.zip")
        createNestedZipFile(zipPath.toString())

        val outputDir = tempDir.resolve("output").toString()

        every { apiService.apiCallToFile(any(), any(), any()) } answers {
            val targetFile = secondArg<File>()
            zipPath.toFile().copyTo(targetFile, overwrite = true)
        }

        ZipUtil.downloadAndUnzip("https://example.com/nested.zip", outputDir, apiService)

        assertTrue(Files.exists(Paths.get(outputDir, "folder1", "folder2", "nested.txt")))
    }

    @Test
    fun `downloadAndUnzip should handle download failure gracefully`(@TempDir tempDir: Path) {
        val outputDir = tempDir.resolve("output").toString()

        every { apiService.apiCallToFile(any(), any(), any()) } throws IOException("Download failed")

        assertThrows<IOException> {
            ZipUtil.downloadAndUnzip("https://example.com/bad.zip", outputDir, apiService)
        }
    }

    @Test
    fun `downloadAndUnzip should extract nothing from corrupted zip file`(@TempDir tempDir: Path) {
        val corruptedZip = tempDir.resolve("corrupted.zip")
        Files.write(corruptedZip, "This is not a valid zip file".toByteArray())

        val outputDir = tempDir.resolve("output").toString()

        every { apiService.apiCallToFile(any(), any(), any()) } answers {
            val targetFile = secondArg<File>()
            corruptedZip.toFile().copyTo(targetFile, overwrite = true)
        }

        // ZipInputStream silently returns null entries for non-zip data rather than throwing
        ZipUtil.downloadAndUnzip("https://example.com/corrupted.zip", outputDir, apiService)

        // Output directory is created but should contain no files
        val outputDirFile = File(outputDir)

        assertTrue(outputDirFile.exists())
        assertFalse(outputDirFile.listFiles()?.isNotEmpty() == true)
    }

    @Test
    fun `downloadAndUnzip should reject zip entries with path traversal`(@TempDir tempDir: Path) {
        val maliciousZip = tempDir.resolve("malicious.zip")
        createZipSlipFile(maliciousZip.toString())

        val outputDir = tempDir.resolve("output").toString()

        every { apiService.apiCallToFile(any(), any(), any()) } answers {
            val targetFile = secondArg<File>()
            maliciousZip.toFile().copyTo(targetFile, overwrite = true)
        }

        val exception = assertThrows<IOException> {
            ZipUtil.downloadAndUnzip("https://example.com/malicious.zip", outputDir, apiService)
        }
        assertContains(exception.message!!, "Zip entry outside target dir")
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

    private fun createZipSlipFile(zipPath: String) {
        ZipOutputStream(FileOutputStream(zipPath)).use { zos ->
            val entry = ZipEntry("../../escape.txt")
            zos.putNextEntry(entry)
            zos.write("I should not be extracted".toByteArray())
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
