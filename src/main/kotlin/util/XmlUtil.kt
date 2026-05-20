package util

import uk.org.siri.siri21.Siri
import java.io.StringWriter

object XmlUtil {
    fun toXml(siri: Siri, formatOutput: Boolean = true): String {
        val marshaller = SharedJaxbContext.createMarshaller(formatOutput)

        val writer = StringWriter()
        marshaller.marshal(siri, writer)
        return writer.toString()
    }
}