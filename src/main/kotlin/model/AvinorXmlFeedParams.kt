package model

import org.gibil.AvinorApiConfig

data class AvinorXmlFeedParams(
    val airportCode: String,
    val timeFrom: Int = AvinorApiConfig.TIME_FROM_DEFAULT,
    val timeTo: Int = AvinorApiConfig.TIME_TO_DEFAULT,
    val direction: String? = null
) {
    init {
        // Enforce basic format rules immediately upon creation
        require(airportCode.length == 3) {
            "Invalid airport code format: $airportCode. Must be 3 characters."
        }

        // Move the time validation logic here
        require(timeTo in AvinorApiConfig.TIME_TO_MIN_NUM..AvinorApiConfig.TIME_TO_MAX_NUM) {
            "TimeTo parameter is outside of valid range, can only be between 7 and 336 hours"
        }
        require(timeFrom in AvinorApiConfig.TIME_FROM_MIN_NUM..AvinorApiConfig.TIME_FROM_MAX_NUM) {
            "TimeFrom parameter is outside of valid range, can only be between 1 and 36 hours"
        }
    }
}