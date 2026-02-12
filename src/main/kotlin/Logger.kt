package org.gibil

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * a simple logger class with the logMessage method in it
 */
class Logger {
    val filePathBase = "logs"

    /**
     * A logger method, meant for when println isn't the best solution
     * @param message What the logger is meant to log, the text inside the created file
     * @param fileName The name of the file
     * @param folder A custom folder or "general" if no argument is given
     * @return creates a .txt file with the given message in it
     * example usage:
     *     val logger = Logger()
     *     logger.logMessage("Hello World", "importantLog.txt")
     *     logger.logMessage("Hello World", "importantLog.txt", "/secrets")
     */
    fun logMessage(message: String, fileName: String, folder: String = "general") {
        val filePath = "$filePathBase/$folder"  // Combine base + folder
        val folderFile = File(filePath)
        folderFile.mkdirs()

        try {
            val file = File(filePath, "$fileName.txt")
            file.writeText(message)

        } catch (e: Exception){
            println("Error: ${e.message}")

            // Fallback to random filename
            val randomNumbers = Random.nextInt(100000000, 999999999).toString()
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val randomFileName = "${date}_${randomNumbers}.txt"
            val file = File(filePath, randomFileName)
            file.writeText(message)
            println("Logged to fallback: ${file.absolutePath}")
        }
    }
}