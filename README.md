# Gibil

> The Sumerian god of fire and smithing.

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=entur_gibil&metric=alert_status)](https://sonarcloud.io/dashboard?id=entur_gibil)
![Github Workflow](https://github.com/entur/gibil/actions/workflows/ci-cd.yml/badge.svg)

Real-time flight data adapter that polls the Avinor XML feed, converts it to SIRI-ET format, and pushes it to subscribers via [Anshar](https://github.com/entur/anshar).

## Data flow

**Input:** Avinor XML feed → merged `Flight` objects (departure + arrival)

**Output:** SIRI-ET XML pushed to Anshar subscribers on change

Only domestic flights (`domInt=D`) within a ±20 min / +7 h time window are processed. Airports are polled in batches every 2 minutes.

## Configuration

Key properties in `application.properties` (overridable via environment variables in Kubernetes):

```properties
avinor.api.base-url-xmlfeed=https://asrv.avinor.no/XmlFeed/v1.0
avinor.api.base-url-airport-names=https://asrv.avinor.no/airportNames/v1.0
entur.api.base-url-stop-places=https://api.entur.io/stop-places/v1/read/stop-places

# Allowlist of hostnames permitted as subscriber addresses (SSRF protection)
siri.allowed-subscriber-hosts=localhost,127.0.0.1

# Optional: override the NeTEx data directory (defaults to GCP path in cluster)
# gibil.extime.path=/custom/path/extimeData
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/siri` | Full SIRI-ET XML for all airports (~55 API calls) |
| GET | `/avinor?airport=OSL` | Raw Avinor XML for a single airport (debug) |
| POST | `/subscribe` | Register a SIRI-ET subscription |
| POST | `/unsubscribe` | Terminate a SIRI-ET subscription |

## Development

Requires Java 21 and Maven.

```bash
mvn spring-boot:run   # Run locally
mvn verify            # Build and run tests
mvn test              # Run tests only
```

When running locally, Gibil downloads and unpacks the NeTEx timetable data from GCP automatically on startup.
