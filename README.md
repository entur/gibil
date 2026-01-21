# Gibil

> Lord of fire and smithing

Gibil is going to be a backend application for realtime flight data provided by Avinor in their custom XML format.
This data is converted into Siri-ET compliant data before ultimately being injected into [Anshar](https://github.com/entur/anshar)

This data will provide information about deviation in planned flights for the majority of Norwegian airports.

# Dataflows

## Input

Gibil makes use of flight timetable and Airport data provided by Avinor.

Flight timetables:
https://asrv.avinor.no/XmlFeed/v1.0

Airport names:
https://asrv.avinor.no/airportNames/v1.0

## Output 

Gibil converts and outputs Siri-ET compliant data

