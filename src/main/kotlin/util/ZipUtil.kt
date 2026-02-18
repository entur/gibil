package org.gibil.util

import org.gibil.service.ApiService
import java.io.*
import java.util.zip.ZipInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Utility object for downloading and extracting ZIP archives.
 *
 * Used to fetch NeTEx data from remote storage and extract it to a local directory
 * for service journey parsing.
 */
object ZipUtil {

    /**
     * Extracts a ZIP archive to the specified output directory.
     * Creates the output directory if it does not exist. Includes Zip Slip protection
     * that validates each entry's resolved path stays within [outputDir], throws
     * [IOException] if a path traversal attempt is detected.
     *
     * @param zipFilePath absolute or relative path to the ZIP file to extract.
     * @param outputDir target directory for the extracted contents. Created if absent.
     * @throws IOException if a ZIP entry resolves outside [outputDir] (Zip Slip), or if
     *   any I/O error occurs during extraction.
     */
    private fun unzipFile(zipFilePath: String, outputDir: String) {
        val outputDirFile = File(outputDir).canonicalFile
        outputDirFile.mkdirs()

        ZipInputStream(FileInputStream(zipFilePath)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val destFile = outputDirFile.resolve(entry.name).canonicalFile
                if (!destFile.startsWith(outputDirFile)) {
                    throw IOException("Zip entry outside target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    Files.copy(zip, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
            }
        }
    }

    /**
     * Downloads a ZIP archive from [url] and extracts its contents into [outputDir].     *
     * The file is downloaded to a temporary location and deleted after extraction,
     * even if an error occurs during unzipping.
     *
     * @param url URL of the ZIP archive to download.
     * @param outputDir target directory for the unzipped contents. Created if absent.
     * @param apiService the [ApiService] used to perform the HTTP download.
     * @throws IOException if the download or extraction fails.
     */
    fun downloadAndUnzip(url: String, outputDir: String, apiService: ApiService) {
        val tempFile = File.createTempFile("netex", ".zip")
        try {
            apiService.apiCallToFile(url, tempFile)
            unzipFile(tempFile.path, outputDir)
        } finally {
            tempFile.delete()
        }
    }
}
