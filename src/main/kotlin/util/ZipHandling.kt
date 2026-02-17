package org.gibil.util

import java.io.*
import java.net.HttpURLConnection
import java.util.zip.ZipInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.net.URL
import java.nio.file.Paths

class ZipHandlingFailure(message: String) : Exception(message)

/**
 * Utility class for handling ZIP files, including downloading and unzipping.
 */
class ZipHandling {
    //temp storage of the downloaded file, this is used to store the file before unzipping it, and is deleted after unzipping
    val savePath = System.getProperty("java.io.tmpdir") + File.separator + "temp.zip"

    /**
     * Downloads a file from the specified URL and saves it to the given path.
     * @param url The URL of the file to download.
     */
    private fun downloadFile(url: String) {
        //todo: update to use OkHttpClient instead
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000  // 10 seconds to establish connection
        connection.readTimeout = 30_000     // 30 seconds to read data

        try {
            connection.inputStream.use { input ->
                Files.copy(input, Paths.get(savePath), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            connection.disconnect()
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
            throw ZipHandlingFailure("Failed to download file from $url: ${e.message}")
        }

        //attempt to unzip file, and always attempt to delete the downloaded file,
        // even if unzipping fails, to avoid leaving temp files around
        try {
            try {
                unzipFile(outputDir)
            } finally {
                File(savePath).delete()  // always clean up, even if unzip fails
            }
        } catch (e: Exception) {
            throw ZipHandlingFailure("Failed to unzip file: ${e.message}")
        }

    }
}