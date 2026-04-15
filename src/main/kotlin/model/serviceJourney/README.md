# ServiceJourney Model

A set of Kotlin data classes for representing **NeTEx** (Network Timetable Exchange) service journey data, using JAXB annotations for XML binding.

---

## Classes

### `ServiceJourney`
The root element representing a single service journey (a vehicle trip on a specific route).

| Field | XML Mapping | Description |
|---|---|---|
| `serviceJourneyId` | `@id` attribute | Unique identifier for the journey |
| `publicCode` | `<PublicCode>` element | Public-facing trip/line code |
| `lineRefElement` | `<LineRef>` element | Reference to the line this journey belongs to |
| `dayTypeRefs` | `<dayTypes><DayTypeRef>` elements | Which day types this journey operates on |
| `passingTimesWrapper` | `<passingTimes>` element | Container for all passing times |

**Computed properties:**

| Property | Returns |
|---|---|
| `dayTypes` | `List<String>` — the `ref` values from all `DayTypeRef` entries |
| `departureTime` | `List<String>` — departure times from all passing times (see note below) |
| `arrivalTime` | `List<String>` — arrival times from all passing times (see note below) |
| `lineRef` | `String?` — shortcut to the line reference string |

---

### `DayTypeRef`
A reference to a day type (e.g. weekday, weekend), identified by a `ref` attribute.

```kotlin
DayTypeRef(ref = "AVI:DayType:2023829901-Feb_Wed_04")
```

---

### `LineRefWrapper`
Wraps a `ref` attribute pointing to a line identifier.

```kotlin
LineRefWrapper(ref = "AVI:Line:DY_OSL-TRD")
```

---

### `PassingTimesWrapper`
Container holding a list of `TimetabledPassingTime` entries, mapped from the `<passingTimes>` XML element.

---

### `TimetabledPassingTime`
Represents a single stop's scheduled times within a journey.

| Field | XML Mapping | Description |
|---|---|---|
| `departureTime` | `<DepartureTime>` | Scheduled departure (nullable) |
| `arrivalTime` | `<ArrivalTime>` | Scheduled arrival (nullable) |

> Note: The first stop typically has only a `DepartureTime`, and the last stop typically has only an `ArrivalTime`. Both fields are therefore nullable.

---

## Why departure and arrival times are lists

NeTEx models flights as if they were bus/rail **lines**, not individual dated trips. A flight like `DY770 OSL→TRD` is represented as a single `ServiceJourney` with all its operating dates listed as `DayTypeRef` entries — rather than a separate journey per date.

However, a recurring flight with the same IATA code and airport pair does not always depart and arrive at the same time across its entire schedule. For example, `DY770` might depart at `20:40` for most of the year but at `20:25` during a seasonal schedule change. Since NeTEx groups all of these occurrences under one `ServiceJourney`, each schedule variant becomes a separate `TimetabledPassingTime` entry inside `<passingTimes>`.

This is why `departureTime` and `arrivalTime` are exposed as `List<String>` rather than single values — one entry per distinct time variant found in the journey's passing times. When a flight has a consistent schedule year-round, the lists will contain only one element each.

---

## XML Structure

The following is a real example from the **Norwegian NeTEx feed** for the Oslo–Trondheim route (operator: Norwegian Air / DY), which is the primary dataset this model is used with.

The file is a `PublicationDelivery` document containing a `TimetableFrame` with many `ServiceJourney` elements. The model unmarshals each `<ServiceJourney>` block like this:

```xml
<ServiceJourney version="1" id="AVI:ServiceJourney:DY8404-01-523271305">
  <dayTypes>
    <DayTypeRef ref="AVI:DayType:2023829901-Mar_Sat_28"/>
  </dayTypes>
  <PublicCode>DY8404</PublicCode>
  <LineRef ref="AVI:Line:DY_OSL-TRD" version="1"/>
  <passingTimes>
    <TimetabledPassingTime version="1" id="AVI:TimetabledPassingTime:...">
      <DepartureTime>08:45:00</DepartureTime>
    </TimetabledPassingTime>
    <TimetabledPassingTime version="1" id="AVI:TimetabledPassingTime:...">
      <ArrivalTime>09:40:00</ArrivalTime>
    </TimetabledPassingTime>
  </passingTimes>
</ServiceJourney>
```

**After unmarshalling, this produces:**

```kotlin
ServiceJourney(
    serviceJourneyId = "AVI:ServiceJourney:DY8404-01-523271305",
    publicCode      = "DY8404",
    lineRef         = "AVI:Line:DY_OSL-TRD",
    dayTypes        = listOf("AVI:DayType:2023829901-Mar_Sat_28"),
    departureTime   = listOf("08:45:00"),
    arrivalTime     = listOf("09:40:00")
)
```

---

## Dataset notes

The example feed (`AVI_AVI-Line-DY_OSL-TRD_Oslo-Trondheim.xml`) covers:

- **Route:** Oslo Gardermoen (OSL) ↔ Trondheim Værnes (TRD)
- **Operator:** Norwegian Air (DY)
- **Validity:** 2026-02-02 → 2027-02-04
- **Timezone:** Europe/Oslo
- **NeTEx namespace:** `http://www.netex.org.uk/netex`
- **Total ServiceJourneys:** ~78 (mix of outbound OSL→TRD and inbound TRD→OSL)
- **ID format:** `AVI:ServiceJourney:<FlightNumber>-<variant>-<JourneyPatternId>`
- **DayTypeRef format:** `AVI:DayType:<scheduleId>-<Month>_<Weekday>_<Day>` — one ref per operating date, so a journey running on many dates will have a long list of `DayTypeRef` entries
- **PublicCode examples:** `DY750`, `DY760`, `DY770`, `DY8404`, `DY9999`

---

## Dependencies

- `jakarta.xml.bind` — JAXB annotations for XML serialization/deserialization
- `org.gibil.util.ServiceJourneyModel` — provides the `NETEX_NAMESPACE` constant (`http://www.netex.org.uk/netex`) used across all elements
