# Gibil

> The Sumerian god of fire and smithing.

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=entur_gibil&metric=alert_status)](https://sonarcloud.io/dashboard?id=entur_gibil)
![Github Workflow](https://github.com/entur/gibil/actions/workflows/ci-cd.yml/badge.svg)

Real-time flight data adapter that polls the Avinor XML feed for all Norwegian domestic airports tracked by Avinor, converts flights to SIRI-ET format, resolves NeTEx service journeys, and pushes updates to subscribers via [Anshar](https://github.com/entur/anshar).

## Data flow

```
Avinor XML Feed (airport batches of 5)
        │
        ▼
FlightAggregationService
  ── stitch arrival + departure events into UnifiedFlight chains
  ── filter: 20 min past / 24 h future
        │
        ▼
FlightStateCache
  ── hash-based change detection
        │
        ▼
ServiceJourneyResolver
  ── match against NeTEx timetable data
  ── populate serviceJourneyRef & lineRef
        │
        ▼
SiriETMapper
  ── convert to SIRI EstimatedVehicleJourneys
  ── resolve quay IDs via AirportQuayService
        │
        ▼
SubscriptionManager
  ── push SIRI-ET XML to registered subscribers
  ── auto-terminate after 5 consecutive failures
```

Only domestic flights are processed. Svalbard (LYR) is classified as international by Avinor but treated as domestic by Gibil. Airports are polled in batches every 2 minutes (7-minute initial delay after startup).

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/siri` | Full SIRI-ET XML for all airports (30-60s) |
| GET | `/avinor?airport=OSL` | Raw Avinor XML for a single airport (debug) |
| POST | `/subscribe` | Register a SIRI-ET subscription (XML body) |
| POST | `/unsubscribe` | Terminate a SIRI-ET subscription (XML body) |

## Configuration

Key properties in `application.properties` (overridable via environment/ConfigMap in Kubernetes):

```properties
avinor.api.base-url-xmlfeed=https://asrv.avinor.no/XmlFeed/v1.0
avinor.api.base-url-airport-names=https://asrv.avinor.no/airportNames/v1.0
entur.api.base-url-stop-places=https://api.entur.io/stop-places/v1/read/stop-places

# Override the NeTEx data directory (defaults to GCP path in cluster)
# org.gibil.extime.data-file=/custom/path/extimeData
```

## Tech stack

- **Kotlin** on **Java 21** with **Spring Boot 4**
- **OkHttp** for API calls, **Kotlinx Coroutines** for concurrent batching
- **JAXB** for XML marshalling (Avinor, SIRI, NeTEx)
- **MockK** + **JUnit 5** for testing, **JaCoCo** + **SonarQube** for coverage
- **Docker** (Alpine + Java 21), **Helm** + **Kubernetes** for deployment
- **GitHub Actions** CI/CD: build, SonarQube scan, Docker build/push, deploy to dev/tst/prd

## Development

Requires **Java 21** and **Maven**.

```bash
mvn spring-boot:run   # Run locally
mvn verify            # Build and run tests
mvn test              # Run tests only
```

When running locally, Gibil downloads and unpacks the NeTEx timetable data from GCP automatically on startup.
