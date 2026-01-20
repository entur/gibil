package handler

import model.avinorApi.Airport
import util.SharedJaxbContext
import java.io.StringReader
import java.io.StringWriter

open class AvinorScheduleXmlHandler {

    open fun unmarshallXmlToAirport(xmlData: String): Airport {
        try {
            val unmarshaller = SharedJaxbContext.createUnmarshaller()
            return unmarshaller.unmarshal(StringReader(xmlData)) as Airport

        } catch (e: Exception) {
            throw RuntimeException("Error parsing Airport", e)
        }
    }

    fun marshallAirport(airport: Airport): String {
        try {
            val marshaller = SharedJaxbContext.createMarshaller(true)
            return StringWriter().use { writer ->
                marshaller.marshal(airport, writer)
                writer.toString()
            }
        } catch (e: Exception) {
            throw RuntimeException("Error marshalling airport", e)
        }
    }

}