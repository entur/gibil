package org.gibil

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * a simple logger class with the logMessage method in it
 */
class Logger {
    val filePathBase = if (File("/app/logs").exists()) "/app/logs" else "logs"

    /**
     * A logger method, meant for when println isn't the best solution
     * @param message What the logger is meant to log, the text inside the created file
     * @param fileName The name of the file
     * @param folder A custom folder or "general" if no argument is given
     * @return creates a .txt file with the given message in it
     * example usage:
     *     val logger = Logger()
     *     logger.logMessage("Hello World", "importantLog")
     *     logger.logMessage("Hello World", "importantLog", "secrets")
     */
    fun logMessage(message: String, fileName: String, folder: String = "general") {
        val filePath = "$filePathBase/$folder"  // Combine base + folder
        val filePathStandard = "$filePathBase/general"
        val folderFile = File(filePath)

        if (!(File(filePathStandard).exists() && File(filePathStandard).isDirectory)){
            // Ensure the base "general" folder exists
            File(filePathStandard).mkdirs()
        }

        try {
            folderFile.mkdirs()
            val file = File(filePath, "$fileName.txt")
            file.writeText(message)

        } catch (e: Exception){
            try {
                //attempts rerun in base folder
                println("Error: ${e.message}")

                val file = File(filePathStandard, "$fileName.txt")
                file.writeText(message)

                println("Logged to fallback: ${file.absolutePath}")
            } catch (e: Exception) {
                //if that also fails, rerun with a random filename and base folder
                println("Error: ${e.message}")

                // Fallback to random filename
                val randomNumbers = Random.nextInt(100000000, 999999999).toString()
                val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                val randomFileName = "${date}_${randomNumbers}.txt"
                val file = File(filePathStandard, randomFileName)
                file.writeText(message)
                println("Logged to fallback: ${file.absolutePath}")
            }
        }
    }
}