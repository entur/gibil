package org.example

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Marshaller
import org.example.netex.Airport
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter

class AvinorScheduleXmlHandler {

    companion object {
        private val context: JAXBContext = JAXBContext.newInstance(Airport::class.java)
    }

    fun unmarshall(xmlData: String): Airport {
        try {
            val unmarshaller = context.createUnmarshaller()
            return unmarshaller.unmarshal(StringReader(xmlData)) as Airport

        } catch (e: Exception) {
            throw RuntimeException("Error parsing Airport", e)
        }
    }

    fun marshall(airport: Airport): String {
        try {
            val marshaller = context.createMarshaller().apply {
                setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
                setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
            }
            return StringWriter().use { writer ->
                marshaller.marshal(airport, writer)
                writer.toString()
            }
        } catch (e: Exception) {
            throw RuntimeException("Error marshalling airport", e)
        }
    }

}