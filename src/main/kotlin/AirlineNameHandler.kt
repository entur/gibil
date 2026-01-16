import org.example.AvinorApiHandler
import java.io.File

/**
 * Is the handler for finding, fetching, validating, and saving airlinenames. Saves matching sets of airlinename-codes and airlinenames both to a mutable map and to a local json file.
 * @param cacheFile is the filename where the local json storage of the airlines mutablemap information is supposed to be saves/fetched. Standard is airlines.json, other param is mostly only for testing
 */
class AirlineNameHandler(private val cacheFile: String = "airlines.json") {
    private val airlines = mutableMapOf<String, String>()

    init {
        loadCache() //ensures the class is loaded with airlinenames information
    }

    /**
     * Fetches airlineNames in XML format from the api (url value)
     */
    private fun fetchAirlineXml(): String {
        val url = "https://asrv.avinor.no/airlineNames/v1.0"

        val avinorApiHandler = AvinorApiHandler()
        val result = avinorApiHandler.apiCall(url)
        if (result != null) { //if there's a result
            return result
        }
        return "Error: No response from airlinenames api"
    }

    /**
    Gets XML data from fetchairlinexml and then uses regex to extract the information, then places the value in the index of the matching airlinename code then puts this in the airlines map
     saveCache() is then run to save to local json file
     */
    public fun update() {
        val xmlData = fetchAirlineXml()

        airlines.clear()

        // Simple regex to extract code and name from XML
        val pattern = """code="([^"]+)"\s+name="([^"]+)"""".toRegex()
        pattern.findAll(xmlData).forEach { match ->
            val code = match.groupValues[1]
            val name = match.groupValues[2]
            airlines[code] = name
        }

        saveCache()
    }

    /**
     * Finds airlinename based on airlinename-code
     * @param code airlineNameCode, looks for matching airlineName in airlines
     * @return returns name of airlinenamecode, example; AA returns American Airlines. returns an error code if not found
     */
    public fun getName(code: String): String?{
        if(airlines[code] != null){
            return airlines[code]
        } else {
            return "Airlinename not found"
        }
    }

    /**
     * checks if the airlinename-code exists in the airlines name collection
     * @param code airlineNameCode, examples; RU, RY, SK, SNX
     * @return returns a boolean based on if the airlinenamecode exists
     */
    public fun isValid(code: String): Boolean = airlines.containsKey(code)

    /**
     * saves the airlines mutable map to a json file locally
     */
    private fun saveCache() {
        val json = airlines.entries.joinToString(",\n  ", "{\n  ", "\n}") {
            """"${it.key}": "${it.value}""""
        }
        File(cacheFile).writeText(json)
    }

    /**
     * Loads information from local json file into airlines-map, or calls update() to fetch airlinename-info from avinor api
     */
    private fun loadCache() {
        val file = File(cacheFile)
        if (!file.exists()){
            update()
            return
        }

        val json = file.readText()
        val pattern = """"([^"]+)":\s*"([^"]+)"""".toRegex()
        pattern.findAll(json).forEach { match ->
            airlines[match.groupValues[1]] = match.groupValues[2]
        }
    }
}

