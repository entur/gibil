package org.example

fun main() {
    val flyplass = readln()

    val avinorApi = avinorApiHandling()

    val exampleQueryAPI = avinorApi.apiCall(
        airportCodeParam = flyplass,
        directionParam = "A",
        lastUpdateParam = "2026-01-01T09:30:00Z",
        serviceTypeParam = "E",
        timeToParam = 336,
        timeFromParam = 24,
    )

    val xmlData = avinorApi.apiCall("OSL", directionParam = "D")
    if (xmlData != null) {
        // Send to XML handling function
    } else {
        println("Failed to fetch XML data")
    }

    println(exampleQueryAPI)
}