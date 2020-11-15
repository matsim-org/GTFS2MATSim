---
title: "RunVehicleCirculation Documentation"
author: "gmarburger"
date: "11/8/2020"
output: markdown
---

## Documentation of Java classes for CreateVehicleCirculation for GTFS2MATSim
---

<br>
<p align="justify">This is the documentation for the program RunVehicleCirculation which creates vehicle circulations for MATSim scenarios. RunVehicleCirculation is integrated into the program GTFS2MATSim, such that TransitSchedules may be created with vehicle circulations. </p>
<p align="justify">Each method has its own explanation. Firstly, input is written in italics along with an explanation of the input and what is expected from the user. Secondly a short description of the method follows including some of the key elements. of the function. Some methods had design choices which are also briefly explained. </p>

<details>
<summary><strong>create()</strong></summary>

<br>
<i>scenario</i> A MATSim scenario<br>
<i>minTimeToWait</i> Minimal time difference for a vehicle after ending a route and serving the next Departure<br>
<i>overrideDelay</i> If minTimeToWait should be overwritten. Useful to create less S + U Vehicles in the Berlin Scenario<br>
<br>
<p align="justify">This is the main part of the program. It will call other functions so as to create vehicle workings for the scenario considering the integer minTimeToWait. If overrideDelay is set to true the program will override the integer and set it to 900 seconds for TransitRoutes from the S-Bahn and U-Bahn.</p>
<br>

<details>
<summary> Design Choices </summary>
<p align="justify">Firstly two Maps are being created to document which new TransitVehicles should be added to the TransitVehicles dataset as well as which nodes should be connected. At the end of the code it will call another function addTransitVehicles() to add the previously created vehicles.</p>
<p align="justify">While iterating through the TransitSchedule this function will call other functions. Firstly will it call getMapOfAllDeparturesOnLine() to create a TreeMap. Instead of iterating through each TransitLine I found it easier to simply iterate through a Map with Departures sorted by DepartureTime.</p>
<p align="justify">If the TransitVehicle of a Departure was already created by this function the Departure will remain unchanged. If however the Departure has not yet been modified a new vehicle will be created and assigned to this Departure. It will be added to the previously mentioned Map. The integer used to create these Ids will be increased by one.</p>
<p>The function getNextDepartureFromEndstation() will be called to change future Departures.</p>
<p>The end of the function is marked by the output of a string containing the amount of newly created vehicles.</p>
</details>

</details>
<br>

---

<details>
<summary> <strong>getMapOfAllDeparturesOnLine()</strong></summary>
<br>
<i>transitLine</i> A MATSim TransitLine <br>
<br>

<p align="justify">Since this program is being used to create vehicle workings for an entire TransitLine, this method collects every Departure of every TransitRoute and orders them by departure time. </p>
<br>
<details>
<summary> Design Choices </summary>
<p align="justify"> Due to complications with other methods and writing my own comparator, I decided to use a String replacing a comparator for the TreeMap. Generally this should not be an issue, even though it might not appear clean. The keys in the Map are the departure times from the Departures, they are ordered as Strings. The Strings consist of 27 characters, the first 7 indicate which time the TransitVehicle leaves the station. The other 20 are only used to create a unique key for the Departure. Currently DepartureIDs are unique, with a length of 11 characters. These last 20 are however needed, since it is possible to have the exact same departure time at two different stops.</p>
</details>
<br>

---
<details>
<summary><strong>getUmlaufVecId()</strong></summary>
<br>

<i> transitLine</i> A MATSim TransitLine<br>
<i> iteration </i> This is an integer that should be used for an Id <br>
<br>
<p align="justify"> This method creates a new TransitVehicleID containing information about the TransitLine. </p>
</details>
<br>

---
<details>
<summary><strong>getRouteFromDeparture()</strong></summary>
<br>
<i> transitLine </i> A MATSim TransitLine <br>
<i> departure </i> A MATSim Departure <br>
<br>

<p align="justify"> Returns the TransitRoute which corresponds with departure. </p>
<br>
<details>
<summary> Design Choices </summary>
<p align="justify"> Currently GTFS2MATSim Departures already contain information about the TransitRoute. A simple String parser can return information about which TransitRoute is being served by a TransitVehicle. After looking at other programs that create TransitVehicles I preferred a more complicated approach at returning the TransitRoute, since so as future programs on this basis are less prone to complications.</p>
<p align="justify">I am aware that this method requires more resources due to large TransitLines especially in Berlin, however I believe that iterating over each TransitRoute and Departure is justified.</p>
</details>
</details>
<br>

---
<details>
<summary><strong>getEndStationFromRoute()</strong></summary>
<br>
<i> transitRoute </i> A MATSim TransitRoute <br>
<br>
<p align="justify"> Returns the last stop of a TransitRoute.</p>
</details>
<br>

---
<details>
<summary><strong>getNextDepartureFromEndstation()</strong></summary>
<br>
<i>mapOfAllDeparturesOnLine</i> A Map which holds all Departures from a TransitLine<br>
<i>transitLine</i> A MATSim TransitLine<br>
<i>currentDeparture</i> The Departure which is currently being modified, the next modified Departure will use this vehicle<br>
<i>setOfVecOnLine</i> A Set of all vehicles which have already been created by this program<br>
<i>minWaitTimeAtEndStation</i> Minimal time a vehicle should wait until it servers the next Departure<br>
<i>network</i> A MATSim network<br>
<i>iteratorLinkId</i> Integer which is used for unique LinkIds<br>
<i>setOfCreatedLinks</i> Set of all previously created links<br>
<i>overrideMinDelay</i> If minTimeToWaitAtEndStop should be overwritten. Useful to create less S + U Vehicles in Berlin Scenario<br>
<br>
<p align="justify">Sets following parameters: A VehicleId from the currentDeparture, the last Stop of the transitLine and the duration the vehicle has to wait to serve a new Departure. This parameter depends on the Boolean overrideMinDelay. If it is set to <i>true</i> transitLines of S-Bahn or U-Bahn will have a turn-over time of 15 minutes, else it will have a turn-over time of minWaitTimeAtEndStation. This turn-over time symbolizes the time the vehicle waits at the last stop of the transitLine before it could start a new Departure at this node.</p>
<p align="justify">To find a new Departure for the vehicle this method iterates over all Departure of mapOfAllDeparturesOnLine to find the next Departure which meets the requirements.</p>
</details>
<br>

---
<details>
<summary><strong>meetsRequirements()</strong></summary>
<br>
<i> departureTimeAtNewLocatiom </i> Departure time at the new stop <br>
<i> earliestPossibleDepartureTime </i> Arrival time of old TransitRoute stop plus min turn-over time <br>
<i>startStopName</i> Name of the next possible stop<br>
<i>endStopName</i> Name of the last stop on the current TransitLine<br>
<br>
<p align="justify"> This method was created to make it possible to interchange filtering options. It is used to check, if a TransitRouteStop can be used as a new starting stop for the TransitVehicle. Currently, it returns <i>true</i> if:
<ol>
    <li>The departure time is greater than the earliest possible starting time</li>
    <li>The stop names are equal or if one is contained in the other</li>
</ol>
<br>
<details>
<summary>Design Choices</summary>
<p align="justify"> This adjustment was made after running into issues with the Berlin bus line M44. It ends in a stop name called "Alt-Buckow" and the next first stop would be called "Alt-Buckow [Dorfteich]". If this method proves faulty it will be changed back to the 2nd element being "stop names have to be equal".</p>
</details>
<br>

---
<details>
<summary><strong>addTransitVehicles()</strong></summary>
<br>
<i> transitVehicles </i> A MATSim TransitVehicles dataset <br>
<i> mapOfVehicles </i> A Map of VehicleIds as key set with their corresponding VehicleType <br>
<br>
<p align="justify">Iterates through the Map and creates for each Id a vehicle with the corresponding VehicleType. Afterwards it is added to the TransitVehicles dataset. </p>
</details>
<br>

---
<details>
<summary><strong>addLinkBetweenEndAndStart()</strong></summary>
<br>
<i>network</i> A MATSim network dataset <br>
<i>startStop</i> The stop which should be connected to the other stop. This will be the to-Node<br>
<i>endStop</i> The stop which should be connected. This will be the from-Node<br>
<i>iterator</i> A unique integer to identify the link<br>
<i>setOfCreatedLinks</i> This is a place-holder and currently not in use, however I might want to add a functionality to identify already created links to be able to reduce the amount of newly created links<br>
<br>
<p align="justify">Reads the Nodes from both stops and afterwards creates a link to connect both. The end of a link is the startStop-Node, the beginning is the endStop-Node. The link is also added to the network.</p>
<p align="justify">The iterator is used to create unique names for the links. The prefix 'umlauf' is used to determine the links created after a simulation has run. The iterator is also increased by one before it is returned.</p>

<details>
<summary>Design Choices</summary>
<p align="justify"> Important aspects of link creation are as follows: <br>
<ul>
    <li><i>length</i> is set to 50, since this is the same length as the loop for a beginning of a TransitRoute. </li>
    <li><i>freespeed</i> is set to 10, which is a big value to insure rapid passage across the link. </li>
    <li><i>capacity</i> is set big enough to store vehicles. </li>
    <li><i>lanes</i> is set to a value greater than 1, so that vehicles could overtake one another. It could be possible for vehicles in the program to have to overtake another to be able to serve a Departure. </li>
</ul>
</details>
</details>
<br>

---
<details>
<summary><strong>writeFiles()</strong></summary>
<br>

<i>scenario</i> a MATSim scenario<br>
<br>

This functionality is currently not in use. It was created so as network, transit-schedule and transit-vehicles files may be created.

</details>