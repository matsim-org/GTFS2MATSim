---
title: "RunehicleCirculation Documentation"
author: "gmarburger"
date: "11/8/2020"
output: html_document
---

## Documentation of java-classes for CreateVehicleCirculation for GTFS2MATSim
---

<br>
<p align="justify">This is the documentation for the program RunVehicleCirculation from the for my Master thesis. It creates Vehicle workings for MATSim scenarios. It is appended to the program GTFS2MATSim, so as TransitSchedules may be created with vehicle workings. The following will explain the required input data as well as some design choices behind the methods. For easier use methods are drop down menus.</p>

<details open>
<summary><strong>create()</strong></summary>

<br>
<i>scenario</i> a the MATSim scenario<br>
<i>minTimeToWait</i> minimal time difference for a vehicle after ending a route and starting the next departure<br>
<i>overrideDelay</i> if the minTimeToWaitAtEndStop should be overritten. Usefull to create less S + U Vehicles in Berlin Scenario<br>
<br>
<p align="justify">This is the main part of the programm. It will call other functions so as to create vehicle workings for the scenario under consideration of the integer minTimeToWait. If the boolean is set to true the program will override the integer and set it to 900 seconds for TransitRoutes from the S and U-Bahn.</p>
<br>

<details open>
<summary> Design Choices </summary>
<p align="justify">Firstly two maps are being created to document which new TransitVehicles should be added to that dataset as well as which nodes need to be connected. At the end of the code it will call another function addTransitVehicles() to add the previously created new vehicles.</p>
<p align="justify">While iterating through the TransitSchedule this function will call other functions. Firstly will it call getMapOfAllDeparturesOnLine() to create a TreeMap. Instead of iterating through each TransitLine I found it easier simply to iterate through a Map with Departures sorted by DepartureTime.</p>
<p align="justify">If the TransitVehicle of a Departure was already created by this method this departure will not change. If however the Departure has not yet been modified a new Vehicle will be created and assigned to this departure. It will be added to the previously mentioned Map. An integer used to create these ID will be increased by one.</p>
<p>The function getNextDepartureFromEndstation() will simply be called which changes future departures.</p>
<p>The end of the function is marked by the output of a string containing the amount of newly created vehicles.</p>
</details>

</details>
<br>

---

<details open>
<summary> <strong>getMapOfAllDeparturesOnLine()</strong></summary>
<br>

</details>

---
<details open>
<summary><strong>getUmlaufVecId()</strong></summary>
<br>

</details>

---
<details open>
<summary><strong>getRouteFromDeparture()</strong></summary>
<br>

</details>

---
<details open>
<summary><strong>getEndStationFromRoute()</strong></summary>
<br>

</details>

---
<details open>
<summary><strong>getNextDepartureFromEndstation()</strong></summary>
<br>

</details>

---
<details open>
<summary><strong>addTransitVehicles()</strong></summary>
<br>

</details>

---
<details open>
<summary><strong>addLinkBetweenEndAndStart()</strong></summary>
<br>

</details>

---
<details open>
<summary><strong>writeFiles()</strong></summary>
<br>

<i>scenario</i> a MATSim scenario<br>
<br>

This functionality is currently not in usse. It was created so as network, transit-schedule and transit-vehicles files may be created.

</details>