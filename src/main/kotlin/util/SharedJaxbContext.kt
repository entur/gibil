package util

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Marshaller
import jakarta.xml.bind.Unmarshaller
import model.avinorApi.Airport
import uk.org.siri.siri21.Siri

/**
 * A shared JAXB context object for the project.
 * If new classes are needed they can be added to the context variable
 */
object SharedJaxbContext {

   val context: JAXBContext = JAXBContext.newInstance(
       Airport::class.java,
       Siri::class.java
   )

    /**
     * Always encoded to UTF-8
     * @param formatOutput boolean. Baseline false, returns compressed string. If set to true returns formatted XML
     * @return Marshaller
     */
    fun createMarshaller(formatOutput: Boolean = false): Marshaller {
        return context.createMarshaller().apply {
            setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
            if (formatOutput) {
                setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
            }
        }
    }

    fun createUnmarshaller(): Unmarshaller {
        return context.createUnmarshaller()
    }
}