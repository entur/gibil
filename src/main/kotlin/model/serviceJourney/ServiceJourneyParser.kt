package model.serviceJourney

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Unmarshaller
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader
import java.io.File
import org.gibil.ServiceJourneyModel

val printLog = ServiceJourneyModel.DEBUG_PRINTING_SJM

class ServiceJourneyParser {

    private val jaxbContext = JAXBContext.newInstance(ServiceJourney::class.java)
    private val unmarshaller: Unmarshaller = jaxbContext.createUnmarshaller()
    private val xmlInputFactory = XMLInputFactory.newInstance()

    /**
     * Parses a single XML file to extract ServiceJourney elements. It uses a streaming approach to efficiently handle large files by unmarshalling only the relevant parts of the XML.
     * @param file The XML file to parse.
     * @return A list of ServiceJourney objects extracted from the file.
     */
    fun parseFile(file: File): List<ServiceJourney> {
        //to populate with service journeys found in the file
        val serviceJourneys = mutableListOf<ServiceJourney>()

        file.inputStream().use { inputStream ->
            val reader: XMLStreamReader = xmlInputFactory.createXMLStreamReader(inputStream)

            while (reader.hasNext()) {
                reader.next()

                // Look for ServiceJourney start elements
                if (reader.isStartElement && reader.localName == "ServiceJourney") {
                    // Unmarshall just this element as a service journey model
                    val journey = unmarshaller.unmarshal(reader) as ServiceJourney
                    serviceJourneys.add(journey)
                }
            }

            reader.close()
        }

        return serviceJourneys
    }

    /**
     * Parses all XML files in a specified folder to extract ServiceJourney elements. It iterates through each XML file in the folder, uses the parseFile method to extract service journeys, and aggregates the results into a single list.
     * @param folderPath The path to the folder containing XML files to parse.
     * @return A list of ServiceJourney objects extracted from all XML files in the folder.
     */
    fun parseFolder(folderPath: String): List<ServiceJourney> {
        val folder = File(folderPath)

        if (!folder.exists() || !folder.isDirectory) {
            throw IllegalArgumentException("$folderPath is not a valid directory")
        }

        //to populate with service journeys found in the folders XML files
        val allJourneys = mutableListOf<ServiceJourney>()

        // Get all XML files in the folder
        folder.listFiles { file -> file.extension.lowercase() == "xml" }?.forEach { xmlFile ->
            if (printLog) {
                println("Parsing: ${xmlFile.name}")
            }
            try {
                //parse the file and add the found service journeys to the allJourneys list
                val journeys = parseFile(xmlFile)
                allJourneys.addAll(journeys)
                if (printLog) {
                    println("  Found ${journeys.size} service journeys")
                }
            } catch (e: Exception) {
                println("  Error parsing ${xmlFile.name}: ${e.message}")
            }
        }

        return allJourneys
    }
}