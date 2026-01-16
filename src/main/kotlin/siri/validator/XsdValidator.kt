package siri.validator

import org.entur.siri.validator.SiriValidator

data class ValidationResult(val isValid: Boolean, val message: String)

class XsdValidator {

    fun validateSirixml(xmlData: String, version: SiriValidator.Version): ValidationResult {
        return try {
            val isValid = SiriValidator.validate(xmlData, version)
            ValidationResult(isValid, if (isValid) "XML is valid." else "XML is not valid.")
        } catch (e: Exception) {
            ValidationResult(false, "Validation error: ${e.message}")
        }
    }
}