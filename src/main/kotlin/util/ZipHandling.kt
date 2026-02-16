package org.gibil.util

import java.io.*
import java.util.zip.ZipInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.net.URL
import java.nio.file.Paths

/**
 * Utility class for handling ZIP files, including downloading and unzipping.
 */
class ZipHandling {
    //temp storage of the downloaded file, this is used to store the file before unzipping it, and is deleted after unzipping
    val savePath = "src/main/resources/temp"

    /**
     * Downloads a file from the specified URL and saves it to the given path.
     * @param url The URL of the file to download.
     */
    private fun downloadFile(url: String) {
        URL(url).openStream().use { input ->
            Files.copy(input, Paths.get(savePath), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Unzips a ZIP file from the specified path to the given output directory.
     * @param outputDir The directory where the contents of the ZIP file should be extracted.
     */
    private fun unzipFile(outputDir: String) {
        File(outputDir).mkdirs()

        ZipInputStream(FileInputStream(savePath)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val path = File(outputDir, entry.name)

                if (entry.isDirectory) {
                    path.mkdirs()
                } else {
                    path.parentFile?.mkdirs()
                    Files.copy(zip, path.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
            }
        }
    }

    /**
     * Downloads a ZIP file from the specified URL and unzips it to the given output directory.
     * @param url The URL of the ZIP file to download and unzip. Local file paths can be used, mostly for testing purposes, by using the "file://" prefix
     * @param outputDir The directory where the contents of the ZIP file should be extracted.
     * THIS METHOD ONLY GIVES SOFT ERRORS, MEANING IT PRINTS THE ERROR BUT DOES NOT THROW IT, further error handling is needed if this method is used in various ways and places
     */
    fun downloadAndUnzip(url: String, outputDir: String) {
        try {
            downloadFile(url)
        } catch (e: Exception) {
            println("Error downloading file: ${e.message}")
            return
        }

        try {
            unzipFile(outputDir)
        } catch (e: Exception) {
            println("Error unzipping file: ${e.message}")
        }
    }
}