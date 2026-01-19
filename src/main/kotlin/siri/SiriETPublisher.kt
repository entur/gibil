package siri

import util.SharedJaxbContext
import uk.org.siri.siri21.Siri
import java.io.StringWriter
import java.io.File

class SiriETPublisher {

    fun toXml(siri: Siri, formatOutput: Boolean = true): String {
        val marshaller = SharedJaxbContext.createMarshaller(formatOutput)

        val writer = StringWriter()
        marshaller.marshal(siri, writer)
        return writer.toString()
    }

    fun toFile(siri: Siri, file: File, formatOutput: Boolean) {
        val marshaller = SharedJaxbContext.createMarshaller(formatOutput)

        marshaller.marshal(siri, file)
    }
}