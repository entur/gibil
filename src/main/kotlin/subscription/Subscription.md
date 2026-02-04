# Subscription Module

This module implements a **SIRI (Service Interface for Real Time Information)** publisher/subscriber system for distributing real-time transit data. It allows external applications (like Anshar) to subscribe to SIRI Estimated Timetable (ET) data updates.

## Overview

```
┌─────────────────┐         POST /subscribe          ┌─────────────────┐
│                 │ ──────────────────────────────▶  │                 │
│   Subscriber    │                                  │     Gibil       │
│   (e.g. Anshar) │  ◀────────────────────────────── │   (Publisher)   │
│                 │    SubscriptionResponse + Data   │                 │
└─────────────────┘                                  └─────────────────┘
        ▲                                                    │
        │              Heartbeats & Data Updates             │
        └────────────────────────────────────────────────────┘
```

## Files

| File | Description |
|------|-------------|
| `SiriEndpoint.kt` | REST controller exposing `/subscribe`, `/unsubscribe`, and `/service` endpoints |
| `SubscriptionManager.kt` | Manages active subscriptions, heartbeats, and data push |
| `Subscription.kt` | Data class representing a subscription |
| `SiriHelper.kt` | Utility for creating SIRI XML response objects |
| `SiriETRepository.kt` | In-memory storage for SIRI Estimated Timetable data |
| `HttpHelper.kt` | HTTP client (Ktor) for posting data to subscribers |
| `SiriDataType.kt` | Enum defining supported SIRI data types |

---

## Flow Diagram

### 1. Subscription Creation

```
Subscriber                          Gibil
    │                                 │
    │  POST /subscribe                │
    │  (SIRI SubscriptionRequest)     │
    │────────────────────────────────▶│
    │                                 │
    │                                 ├── Parse request
    │                                 ├── Create Subscription object
    │                                 ├── Store in SubscriptionManager
    │                                 ├── Start heartbeat scheduler
    │                                 ├── Send initial data delivery
    │                                 │
    │  SubscriptionResponse           │
    │◀────────────────────────────────│
    │                                 │
```

### 2. Data Updates (Push)

```
Data Source                     Gibil                        Subscriber
    │                             │                              │
    │  New ET data arrives        │                              │
    │────────────────────────────▶│                              │
    │                             │                              │
    │                             ├── Store in SiriETRepository  │
    │                             ├── Create ServiceDelivery     │
    │                             │                              │
    │                             │  POST to subscriber address  │
    │                             │─────────────────────────────▶│
    │                             │                              │
```

### 3. Heartbeat Mechanism

```
Gibil                                    Subscriber
  │                                           │
  │  HeartbeatNotification                    │
  │  (every heartbeatInterval)                │
  │──────────────────────────────────────────▶│
  │                                           │
  │                    HTTP 200               │
  │◀──────────────────────────────────────────│
  │                                           │
  
  If 5 consecutive heartbeats fail → subscription is terminated
```

---

## Endpoints

### POST `/subscribe`

Creates a new subscription for SIRI data updates.

**Request Body:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Siri xmlns="http://www.siri.org.uk/siri" version="2.1">
  <SubscriptionRequest>
    <RequestTimestamp>2025-02-02T13:31:49Z</RequestTimestamp>
    <Address>http://subscriber-host:port/callback</Address>
    <RequestorRef>SUBSCRIBER_ID</RequestorRef>
    <SubscriptionContext>
      <HeartbeatInterval>PT30S</HeartbeatInterval>
    </SubscriptionContext>
    <EstimatedTimetableSubscriptionRequest>
      <SubscriberRef>SUBSCRIBER_REF</SubscriberRef>
      <SubscriptionIdentifier>SUBSCRIPTION_001</SubscriptionIdentifier>
      <InitialTerminationTime>2025-12-31T23:59:59Z</InitialTerminationTime>
      <EstimatedTimetableRequest version="2.1">
        <RequestTimestamp>2025-02-02T13:31:49Z</RequestTimestamp>
      </EstimatedTimetableRequest>
    </EstimatedTimetableSubscriptionRequest>
  </SubscriptionRequest>
</Siri>
```

**Response:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Siri xmlns="http://www.siri.org.uk/siri" version="2.1">
  <SubscriptionResponse>
    <ResponseTimestamp>2025-02-02T13:31:50Z</ResponseTimestamp>
    <ResponderRef>SUBSCRIPTION_001</ResponderRef>
    <ResponseStatus>
      <ResponseTimestamp>2025-02-02T13:31:50Z</ResponseTimestamp>
      <SubscriptionRef>SUBSCRIPTION_001</SubscriptionRef>
      <Status>true</Status>
    </ResponseStatus>
  </SubscriptionResponse>
</Siri>
```

**What happens:**
1. Parses the subscription request
2. Extracts: address, requestorRef, heartbeatInterval, subscriptionId
3. Creates a `Subscription` object
4. Stores it in `SubscriptionManager`
5. Starts a heartbeat scheduler for the subscription
6. Sends initial data delivery to the subscriber's address
7. Returns a `SubscriptionResponse`

---

### POST `/unsubscribe`

Terminates an existing subscription.

**Request Body:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Siri xmlns="http://www.siri.org.uk/siri" version="2.1">
  <TerminateSubscriptionRequest>
    <RequestTimestamp>2025-02-02T14:00:00Z</RequestTimestamp>
    <RequestorRef>SUBSCRIBER_ID</RequestorRef>
    <SubscriptionRef>SUBSCRIPTION_001</SubscriptionRef>
  </TerminateSubscriptionRequest>
</Siri>
```

**What happens:**
1. Removes subscription from `SubscriptionManager`
2. Stops the heartbeat scheduler
3. Clears failure counter
4. Returns `TerminateSubscriptionResponse`

---

### POST `/service`

One-time request for current SIRI ET data (no subscription).

**Request Body:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Siri xmlns="http://www.siri.org.uk/siri" version="2.1">
  <ServiceRequest>
    <RequestTimestamp>2025-02-02T13:31:49Z</RequestTimestamp>
    <RequestorRef>REQUESTOR_ID</RequestorRef>
    <EstimatedTimetableRequest version="2.1">
      <RequestTimestamp>2025-02-02T13:31:49Z</RequestTimestamp>
    </EstimatedTimetableRequest>
  </ServiceRequest>
</Siri>
```

**Response:** Current SIRI ET data as `ServiceDelivery`

---

## Components Detail

### Subscription.kt

Data class holding subscription information:

```kotlin
data class Subscription(
    val requestTimestamp: ZonedDateTime,  // When subscription was created
    val subscriptionType: SiriDataType,   // Type of data (ESTIMATED_TIMETABLE)
    val address: String,                  // Callback URL for data delivery
    val subscriptionId: String,           // Unique subscription identifier
    val heartbeatInterval: Duration,      // How often to send heartbeats
    val requestorRef: String              // Subscriber identifier
)
```

### SubscriptionManager.kt

Core component managing all subscriptions:

- **`addSubscription()`** - Registers a new subscription and starts heartbeat
- **`terminateSubscription()`** - Removes subscription and stops heartbeat
- **`pushSiriToSubscribers()`** - Pushes new data to all active subscribers
- **Heartbeat mechanism** - Scheduled task that sends heartbeats and tracks failures
- **Failure handling** - After 5 failed heartbeats, subscription is auto-terminated

### SiriETRepository.kt

In-memory storage for SIRI Estimated Timetable data:

- **`add()`** - Adds new ET data and triggers push to all subscribers
- **`all`** - Returns all stored ET data
- Data is keyed by `datedVehicleJourneyRef`

### HttpHelper.kt

Ktor-based HTTP client for outbound requests:

- **`postData()`** - Posts XML data to a URL
- **`postHeartbeat()`** - Sends heartbeat notification to subscriber
- Configurable timeouts (5s socket, 10s connection)
- Verbose logging option

### SiriHelper.kt

Factory methods for creating SIRI response objects:

- **`createSubscriptionResponse()`** - Builds subscription confirmation
- **`createTerminateSubscriptionResponse()`** - Builds termination confirmation
- **`createSiriEtServiceDelivery()`** - Wraps ET data in ServiceDelivery
- **`createHeartbeatNotification()`** - Builds heartbeat message
- **`resolveSiriDataType()`** - Determines data type from request
- **`resolveSubscriptionId()`** - Extracts subscription ID from request

---

## Configuration

### Spring Beans

All components are Spring-managed:

| Class | Annotation | Description |
|-------|------------|-------------|
| `SiriEndpoint` | `@RestController` | REST endpoints |
| `SubscriptionManager` | `@Repository` | Subscription storage & management |
| `SiriETRepository` | `@Repository` | SIRI data storage |
| `HttpHelper` | `@Component` | HTTP client |

### Timeouts

Defined in `HttpHelper.kt`:
- Socket timeout: 5 seconds
- Connection timeout: 10 seconds

### Failure Threshold

Defined in `SubscriptionManager.kt`:
- Max failed heartbeats: 5 (then subscription is terminated)

---

## Integration with Anshar

To connect Gibil with Anshar:

1. **Anshar subscribes to Gibil:**
   - Anshar sends POST to `http://gibil-host:8080/subscribe`
   - Includes callback address where Anshar receives updates

2. **Gibil pushes data to Anshar:**
   - When new ET data arrives in `SiriETRepository`
   - Gibil POSTs to Anshar's callback address

3. **Heartbeat keeps connection alive:**
   - Gibil sends heartbeats at configured interval
   - If Anshar doesn't respond, subscription is terminated after 5 failures