package model.serviceJourney

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Unmarshaller
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader
import java.io.File

class ServiceJourneyParser {

    private val jaxbContext = JAXBContext.newInstance(ServiceJourney::class.java)
    private val unmarshaller: Unmarshaller = jaxbContext.createUnmarshaller()
    private val xmlInputFactory = XMLInputFactory.newInstance()

    fun parseFile(file: File): List<ServiceJourney> {
        val serviceJourneys = mutableListOf<ServiceJourney>()

        file.inputStream().use { inputStream ->
            val reader: XMLStreamReader = xmlInputFactory.createXMLStreamReader(inputStream)

            while (reader.hasNext()) {
                reader.next()

                // Look for ServiceJourney start elements
                if (reader.isStartElement && reader.localName == "ServiceJourney") {
                    // Unmarshal just this element
                    val journey = unmarshaller.unmarshal(reader) as ServiceJourney
                    serviceJourneys.add(journey)
                }
            }

            reader.close()
        }

        return serviceJourneys
    }

    fun parseFolder(folderPath: String): List<ServiceJourney> {
        val folder = File(folderPath)

        if (!folder.exists() || !folder.isDirectory) {
            throw IllegalArgumentException("$folderPath is not a valid directory")
        }

        val allJourneys = mutableListOf<ServiceJourney>()

        // Get all XML files in the folder
        folder.listFiles { file -> file.extension.lowercase() == "xml" }?.forEach { xmlFile ->
            println("Parsing: ${xmlFile.name}")
            try {
                val journeys = parseFile(xmlFile)
                allJourneys.addAll(journeys)
                println("  Found ${journeys.size} service journeys")
            } catch (e: Exception) {
                println("  Error parsing ${xmlFile.name}: ${e.message}")
            }
        }

        return allJourneys
    }

    fun parseFolderRecursive(folderPath: String): List<ServiceJourney> {
        val folder = File(folderPath)

        if (!folder.exists() || !folder.isDirectory) {
            throw IllegalArgumentException("$folderPath is not a valid directory")
        }

        val allJourneys = mutableListOf<ServiceJourney>()

        folder.walk().forEach { file ->
            if (file.isFile && file.extension.lowercase() == "xml") {
                println("Parsing: ${file.relativeTo(folder)}")
                try {
                    val journeys = parseFile(file)
                    allJourneys.addAll(journeys)
                    println("  Found ${journeys.size} service journeys")
                } catch (e: Exception) {
                    println("  Error parsing ${file.name}: ${e.message}")
                }
            }
        }

        return allJourneys
    }
}

/*fun main() {
    val parser = ServiceJourneyParser()
    println("=== Parsing folder ===")
    val journeysFromFolder = parser.parseFolder("C:\\Users\\niril\\Documents\\.Uia\\is-314\\gibil\\Gibil\\src\\main\\kotlin\\filter\\output")
    println("Total: ${journeysFromFolder.size} service journeys\n")

    // Process the results
    println("=== Sample results ===")
    journeysFromFolder.forEach { journey ->
        println("Service Journey ID: ${journey.serviceJourneyId}")
        println("Public Code: ${journey.publicCode}")
        println("Departure Time: ${journey.departureTime}")
        println("Arrival Time: ${journey.arrivalTime}")
        println("Day Types: ${journey.dayTypes.size}")
        println()
    }
}*/