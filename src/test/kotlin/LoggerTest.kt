package org.gibil


import org.junit.jupiter.api.Assertions.*
import java.io.File
import kotlin.random.Random
import kotlin.test.Test

class LoggerTest {
    val baseDir = File("logs/general")
    val exampleFileName = "TestLog"
    val logger = Logger()

    fun generateRandomString(length: Int = 16): String {
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

        val randomString = (1..length)
            .map { Random.Default.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")

        return randomString
    }

    @Test
    fun `Logfile should be made without errors and exist and text be same`(){
        val randomMessage = generateRandomString(50)
        logger.logMessage(randomMessage, exampleFileName)

        //check if file exists
        val outputFile = baseDir.resolve("$exampleFileName.txt")
        assertTrue(outputFile.exists(), "Output file should exist")

        //check text inside file
        val content = outputFile.readText()
        assertTrue(content.contains(randomMessage), "Logfile should contain the logged message")
    }

    @Test
    fun `Logfile in custom folder should be made without errors and exist and text be same`(){
        val randomMessage = generateRandomString(50)
        logger.logMessage(randomMessage, exampleFileName, "secrets")
        val customFolder = File("logs/secrets")

        //check if file exists
        val outputFile = customFolder.resolve("$exampleFileName.txt")
        assertTrue(outputFile.exists(), "Output file should exist")

        //check text inside file
        val content = outputFile.readText()
        assertTrue(content.contains(randomMessage), "Logfile should contain the logged message")

    }

    @Test
    fun `Try catch should fix bad folder name issue when falling back to standard`(){
        val randomMessage = generateRandomString(50)
        //illegal custom-folder name
        logger.logMessage(randomMessage, "BadFile", ":::___>:>>>;>:_;>_>;>:```^^**^`")

        //check if file exists
        val outputFile = baseDir.resolve("BadFile.txt")
        assertTrue(outputFile.exists(), "Output file should exist")

        //check text inside file
        val content = outputFile.readText()
        assertTrue(content.contains(randomMessage), "Logfile should contain the logged message")

    }

    @Test
    fun `Try catch should fix bad folder name and filename issue when falling back to standard`(){
        val randomMessage = generateRandomString(50)

        var messageFound = false

        //illegal custom-folder name and illegal filename
        logger.logMessage(randomMessage, "<>:/|?*", ":::___>:>>>;>:_;>_>;>:```^^**^`")

        //check if file exists
        baseDir.listFiles { file -> file.extension.lowercase() == "txt" }?.forEach { logFile ->
            val content = logFile.readText()
             if (content.contains(randomMessage)){
                 messageFound = true
             }
        }

        assertTrue(messageFound, "Logfile should contain the logged message")
    }
}