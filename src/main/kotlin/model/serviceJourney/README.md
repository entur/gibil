# JAXB Kotlin Classes for NeTEx ServiceJourney XML Parsing

Parse NeTEx (Network Timetable Exchange) XML files to extract ServiceJourney data efficiently.

## Files

- **ServiceJourney.kt** - Main data class with computed properties for easy access
- **TimetabledPassingTime.kt** - Departure and arrival time data
- **DayTypeRef.kt** - Day type references
- **PassingTimesWrapper.kt** - Wrapper class for passing times (included in ServiceJourney.kt)
- **ServiceJourneyParser.kt** - Streaming XML parser that efficiently extracts only ServiceJourney elements
- **Example files** - Various usage examples

## Gradle Dependencies

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    // Jakarta XML Binding API
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0")
    
    // JAXB Runtime Implementation
    runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.2")
    
    // Kotlin stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}
```

## Quick Start

```kotlin
val parser = ServiceJourneyParser()

// Parse all XML files in a folder
val journeys = parser.parseFolder("path/to/xml/folder")

// Access your data
journeys.forEach { journey ->
    val dayTypes: List<String> = journey.dayTypes
    val publicCode: String = journey.publicCode
    val departureTime: String = journey.departureTime
    val arrivalTime: String = journey.arrivalTime
    val serviceJourneyId: String = journey.serviceJourneyId
    
    println("$publicCode: $departureTime → $arrivalTime")
}
```

## ServiceJourneyParser Methods

### Parse Single File
```kotlin
val file = File("data.xml")
val journeys = parser.parseFile(file)
```

### Parse Folder (Non-Recursive)
```kotlin
// Parses all .xml files in the folder
val journeys = parser.parseFolder("data/xml")
```

### Parse Folder Recursively
```kotlin
// Parses all .xml files in folder and all subfolders
val journeys = parser.parseFolderRecursive("data")
```

## Data Structure

### ServiceJourney Properties

```kotlin
data class ServiceJourney(
    val serviceJourneyId: String        // Full journey ID
    val publicCode: String              // e.g., "SK267"
    val dayTypes: List<String>          // List of all DayTypeRef strings
    val departureTime: String           // e.g., "13:40:00"
    val arrivalTime: String             // e.g., "14:40:00"
)
```

## Usage Examples

### Basic Filtering

```kotlin
val parser = ServiceJourneyParser()
val journeys = parser.parseFolder("data/xml")

// Filter by public code
val sk267 = journeys.filter { it.publicCode == "SK267" }

// Filter by departure time
val morningFlights = journeys.filter { journey ->
    val hour = journey.departureTime.split(":").firstOrNull()?.toIntOrNull() ?: 0
    hour in 6..11
}

// Group by public code
val byCode = journeys.groupBy { it.publicCode }
byCode.forEach { (code, list) ->
    println("$code: ${list.size} journeys")
}
```

### Export to CSV

```kotlin
fun exportToCsv(journeys: List<ServiceJourney>, filename: String) {
    File(filename).bufferedWriter().use { writer ->
        writer.write("ServiceJourneyId,PublicCode,DepartureTime,ArrivalTime,DayTypeCount,DayTypes\n")
        
        journeys.forEach { journey ->
            val dayTypesStr = journey.dayTypes.joinToString(";")
            writer.write("${journey.serviceJourneyId}," +
                        "${journey.publicCode}," +
                        "${journey.departureTime}," +
                        "${journey.arrivalTime}," +
                        "${journey.dayTypes.size}," +
                        "\"$dayTypesStr\"\n")
        }
    }
}

// Usage
val journeys = parser.parseFolder("data/xml")
exportToCsv(journeys, "output.csv")
```

### Statistics

```kotlin
val journeys = parser.parseFolder("data/xml")

println("Total journeys: ${journeys.size}")
println("Unique public codes: ${journeys.map { it.publicCode }.distinct().size}")

// Average day types per journey
val avgDayTypes = journeys.map { it.dayTypes.size }.average()
println("Average day types per journey: %.1f".format(avgDayTypes))

// Find journeys with most day types
val maxDayTypes = journeys.maxByOrNull { it.dayTypes.size }
println("Most day types: ${maxDayTypes?.publicCode} with ${maxDayTypes?.dayTypes?.size} days")
```

### Working with Day Types

```kotlin
val journeys = parser.parseFolder("data/xml")

// Get all unique day types across all journeys
val allDayTypes = journeys.flatMap { it.dayTypes }.toSet()
println("Unique day types: ${allDayTypes.size}")

// Find journeys operating on specific day
val saturdayJourneys = journeys.filter { journey ->
    journey.dayTypes.any { it.contains("Sat") }
}
println("Saturday journeys: ${saturdayJourneys.size}")

// Group by month
val byMonth = journeys.groupBy { journey ->
    journey.dayTypes.firstOrNull()?.let { dayType ->
        dayType.split("-").getOrNull(1)?.take(3) // Extract "Feb", "Mar", etc.
    } ?: "Unknown"
}
```

## Key Features

- **Efficient Streaming**: Uses XMLStreamReader to parse only ServiceJourney elements, ignoring the rest of the large XML structure
- **Namespace Handling**: Properly handles NeTEx XML namespaces (http://www.netex.org.uk/netex)
- **Computed Properties**: Easy access to departure/arrival times and day types
- **Mutable Collections**: Uses `var` and `MutableList` as required by JAXB
- **Error Handling**: Continues parsing even if individual files fail
- **Progress Reporting**: Prints status while parsing folders

## Troubleshooting

### Empty departure/arrival times
- Make sure you're using the latest version of all files (especially TimetabledPassingTime.kt with namespaces)
- The namespace `http://www.netex.org.uk/netex` must be on all element annotations

### "Operation not supported for read-only collection"
- Use `var` instead of `val` for JAXB-annotated properties
- Use `MutableList` instead of `List` for collections

### No journeys found
- Check that your XML files contain `<ServiceJourney>` elements
- Verify the XML uses the NeTEx namespace
- Check the parser output for error messages

## How It Works

The parser uses a streaming approach:
1. Opens XML file with XMLStreamReader
2. Scans for `<ServiceJourney>` start tags
3. Unmarshals only those elements (ignoring routes, lines, frames, etc.)
4. Extracts the 5 key fields you need
5. Returns a clean list of ServiceJourney objects

This is much more efficient than parsing the entire XML structure with nested classes for every element type.

## Notes

- Time fields are strings in "HH:MM:SS" format - convert to LocalTime if needed
- DayType refs are kept as strings - parse them if you need structured date info
- The parser handles both PublicationDelivery root elements and standalone ServiceJourney elements
- Files are read with streaming to handle large XML files efficiently
