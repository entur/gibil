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

    /**
     * Downloads a file from the specified URL and saves it to the given path.
     * @param url The URL of the file to download.
     * @param savePath The local path where the downloaded file should be saved.
     */
    fun downloadFile(url: String, savePath: String) {
        URL(url).openStream().use { input ->
            Files.copy(input, Paths.get(savePath), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Unzips a ZIP file from the specified path to the given output directory.
     * @param zipPath The path to the ZIP file to be unzipped.
     * @param outputDir The directory where the contents of the ZIP file should be extracted.
     */
    fun unzipFile(zipPath: String, outputDir: String) {
        File(outputDir).mkdirs()

        ZipInputStream(FileInputStream(zipPath)).use { zip ->
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

    fun downloadAndUnzip(url: String, outputDir: String) {
        val savePath = "src/main/resources/temp"
        try {
            downloadFile(url, savePath)
        } catch (e: Exception) {
            println("Error downloading file: ${e.message}")
            return
        }

        try {
            unzipFile(savePath, outputDir)
        } catch (e: Exception) {
            println("Error unzipping file: ${e.message}")
        }
    }
}