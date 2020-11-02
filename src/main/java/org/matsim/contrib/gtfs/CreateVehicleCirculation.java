/*********************************************************************** *
  project: org.matsim.
                                                                          
  ********************************************************************** *
                                                                          
  copyright       : (C) 2020 by the members listed in the COPYING,        
                    LICENSE and WARRANTY file.                            
  email           : info at matsim dot org                                
                                                                          
  ********************************************************************** *
                                                                          
    This program is free software; you can redistribute it and/or modify  
    it under the terms of the GNU General Public License as published by  
    the Free Software Foundation; either version 2 of the License, or     
    (at your option) any later version.                                   
    See also COPYING, LICENSE and WARRANTY file                           
                                                                          
  ********************************************************************** */

package org.matsim.contrib.gtfs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV2;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

/**
* @author gmarburger
*/

class CreateVehicleCirculation {
	/** Replaces Vehicles for Vehicles which serve more then one departure but do not change TL. If TL should be changed check interface for LinTim.
	 * 
	 * @param scenario from the MATSim scenario
	 * @param minTimeToWaitAtEndstop minimal time Difference for a vehicle after ending a route and starting the next
	 * @param overrideDelay if the minTimeToWaitAtEndStop should be overritten. Usefull to create less S + U Vehicles in Berlin Scenario
	 * @return
	 */
	static Scenario create(Scenario scenario, int minTimeToWaitAtEndstop, boolean overrideDelay) {
		Network network = scenario.getNetwork();
		Vehicles transitVehicles = scenario.getTransitVehicles();
		TransitSchedule transitSchedule = scenario.getTransitSchedule();
		
		Map<Id<Vehicle>, VehicleType> mapOfVecOnLine = new HashMap<>();
		Map<Id<Link>, Set<Node>> mapOfCreatedLinks = new HashMap<>();
				
		int iteratorLinkId = 0;
		for(TransitLine line : transitSchedule.getTransitLines().values()) {
			TreeMap<String, Departure> mapOfAllDepartures = getMapOfAllDeparturesOnLine(line);

			int iterator = 0;

			for(Departure departureOnLine : mapOfAllDepartures.values()) {
						
				if(!mapOfVecOnLine.keySet().contains(departureOnLine.getVehicleId())) {
					VehicleType vecType = transitVehicles.getVehicles().get(departureOnLine.getVehicleId()).getType();
					departureOnLine.setVehicleId(getUmlaufVecId(line, iterator));
					mapOfVecOnLine.put(departureOnLine.getVehicleId(), vecType);
					iterator +=1;
				}

				iteratorLinkId = getNextDepartureFromEndstation(mapOfAllDepartures, line, departureOnLine, mapOfVecOnLine.keySet(), minTimeToWaitAtEndstop, network, iteratorLinkId,
						mapOfCreatedLinks, overrideDelay);

			}
		}
		
		System.out.println(mapOfVecOnLine.keySet().size() + " were created as vehicle working vehicles!");
		addTransitVehicles(transitVehicles, mapOfVecOnLine);

		return scenario;
	}
	 /** Unused currently.
	  * 
	  * @param scenario
	  */
	static void writeFiles(Scenario scenario) {
		TransitScheduleWriterV2 scheduleWriter = new TransitScheduleWriterV2(scenario.getTransitSchedule());
		String filenameSchedule ="/Users/Gero 1/git/matsim-berlin-MasterArbeit/scenarios/berlin-v5.5-10pct/input/transitSchedule_umlauf.xml";
		scheduleWriter.write(filenameSchedule);
		
		TransitScheduleValidator.validateAll(scenario.getTransitSchedule(), scenario.getNetwork());
		NetworkWriter networkWriter = new NetworkWriter(scenario.getNetwork());
		String filenameNetwork = "/Users/Gero 1/git/matsim-berlin-MasterArbeit/scenarios/berlin-v5.5-10pct/input/network_umlauf.xml";
		networkWriter.write(filenameNetwork);
		
		MatsimVehicleWriter transitVehiclesWriter = new MatsimVehicleWriter(scenario.getTransitVehicles());
		String filenameTransitVehicles = "/Users/Gero 1/git/matsim-berlin-MasterArbeit/scenarios/berlin-v5.5-10pct/input/transitVehicles_umlauf.xml";
		transitVehiclesWriter.writeFile(filenameTransitVehicles);
		
	}
	
	/** Creates a Map of all Departures on all TransitRoutes on a TransitLine 
	 * 
	 * @param transitLine TransitLine which should be used
	 * @return returnes a TreeMap of all Departures of a TransitLine ordered by departure Time.
	 */
	static TreeMap<String, Departure> getMapOfAllDeparturesOnLine (TransitLine transitLine){
		TreeMap<String, Departure> mapOfAllDeparturesOnLine = new TreeMap<>();
		
		int i = 0;
		for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
			for(Departure departure : transitRoute.getDepartures().values()) {
				String idBuilder = departure.getId().toString();
				while(idBuilder.length() < 20) {
					idBuilder = "0" + idBuilder;
				}
				
				String timeBuilder = Integer.toString((int)departure.getDepartureTime());
				while(timeBuilder.length() < 7) {
					timeBuilder = "0" + timeBuilder;
				}
				String stringBuilder = timeBuilder + idBuilder;
				mapOfAllDeparturesOnLine.put(stringBuilder, departure);
				i +=1;
			}
		}
		
		return mapOfAllDeparturesOnLine;
	}
	
	/** Creates a Vehicle ID
	 * 
	 * @param transitLine
	 * @param iteration
	 * @return Returns a Vehicle Id for Vehicle Ciruclation Ids
	 */
	static Id<Vehicle> getUmlaufVecId (TransitLine transitLine, int iteration){
		String umlaufVec = "pt_" + transitLine.getId().toString() + "_umlauf_" + iteration;
		Id<Vehicle> umlaufVecId = Id.create(umlaufVec, Vehicle.class);
		return umlaufVecId;
	}
	
	/** Returns the TransitRoute which contains the Departure
	 * 
	 * @param transitLine liked to the departure to reduce the search radius
	 * @param departure to be analysed
	 * @return the transitRoute from the departure
	 */
	static TransitRoute getRouteFromDeparture (TransitLine transitLine, Departure departure) {
		Id<Departure> departureId = departure.getId();
		
		TransitRoute transitRouteToReturn = null;
		for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
			if(transitRoute.getDepartures().keySet().contains(departureId)) {
				transitRouteToReturn = transitRoute;
			} 
		}
		return transitRouteToReturn;
	}
	
	/** Returns the last Stop of a TransitRoute
	 * 
	 * @param transitRoute Route which should be used
	 * @return returns a TransitRouuteStop which is at the end of the Route.
	 */
	static TransitRouteStop getEndStationFromRoute (TransitRoute transitRoute) {
		int numberOfStopsOnRoute = transitRoute.getStops().size();
		TransitRouteStop endStationName = transitRoute.getStops().get(numberOfStopsOnRoute -1);
		return endStationName;
	}
	
	/** Creates Vehicle Circulations for the next departure
	 * 
	 * @param mapOfAllDeparturesOnLine a Map with all departures of a Line
	 * @param transitLine the TransitLine to iterate through and create vehicle workings for
	 * @param currentDeparture 
	 * @param setOfVecOnLine Set of all vehicle working Ids
	 * @param minWaitTimeAtEndStation minimal time between end of route and next start for a vehicle working
	 * @param network Network of scenario
	 * @param iteratorLinkId Integer for naming links
	 * @param mapOfCreatedLinks Map which contains all previously connected Links
	 * @param overrideMinDelay Boolean, if S + U trains should be set to 900seconds 
	 * @return
	 */
	static int getNextDepartureFromEndstation(TreeMap<String, Departure> mapOfAllDeparturesOnLine, TransitLine transitLine,
			Departure currentDeparture, Set<Id<Vehicle>> setOfVecOnLine, int minWaitTimeAtEndStation, Network network, int iteratorLinkId, Map<Id<Link>, Set<Node>> mapOfCreatedLinks,
			boolean overrideMinDelay) {		
		Id<Vehicle> currentVecId = currentDeparture.getVehicleId();
		Double departureTimeFromCurrentDeparture = currentDeparture.getDepartureTime();
		
		TransitRoute currentTransitRoute = getRouteFromDeparture(transitLine, currentDeparture);
		TransitRouteStop endStop = getEndStationFromRoute(currentTransitRoute);
		String endStationNameOnCurrentLine = endStop.getStopFacility().getName();
		Double travelTimeForCurrentRoute = endStop.getDepartureOffset().seconds();
		
		Double earliestDepartureFromEndStation;
		if(overrideMinDelay) {
			String vehicleTypeS= ".*[S]\\d{1,2}---\\d{5}_109";
			String vehicleTypeU= ".*[U]\\d{1,2}---\\d{5}_400";
			if(transitLine.getId().toString().matches(vehicleTypeS) || transitLine.getId().toString().matches(vehicleTypeU)) {
				minWaitTimeAtEndStation = 15 * 60;
			}
			earliestDepartureFromEndStation = travelTimeForCurrentRoute + minWaitTimeAtEndStation + departureTimeFromCurrentDeparture;
		} else {
			earliestDepartureFromEndStation = travelTimeForCurrentRoute + minWaitTimeAtEndStation + departureTimeFromCurrentDeparture;
		}
		
		Set<Entry<String, Departure>> otherDepartures = mapOfAllDeparturesOnLine.entrySet();
        for(Iterator<Entry<String, Departure>> depatureIterator = otherDepartures.iterator(); depatureIterator.hasNext();){
            Entry<String, Departure> departureToChange = depatureIterator.next();
            
            if(setOfVecOnLine.contains(departureToChange.getValue().getVehicleId())) continue;
            
            Double departureTimeForDepartureToChange = departureToChange.getValue().getDepartureTime();
            TransitRoute transitRouteFromDepartureToChange = getRouteFromDeparture(transitLine, departureToChange.getValue());
            TransitRouteStop startStop= transitRouteFromDepartureToChange.getStops().get(0);
            String startStopName = startStop.getStopFacility().getName();
            
            // maybe try <500m since HERTZAllee isnt the same as Zoologischer Garten, or Other endstops like M44 arent identical with next start station.
            if(startStopName.equals(endStationNameOnCurrentLine) && departureTimeForDepartureToChange > earliestDepartureFromEndStation) {
            	departureToChange.getValue().setVehicleId(currentVecId);
            	
            	if(!endStop.getStopFacility().getId().equals(startStop.getStopFacility().getId())) {
            		iteratorLinkId = addLinkBetweenEndAndStart(network, startStop, endStop, iteratorLinkId, mapOfCreatedLinks);
            	}
            	
            	break;
            }
            
        }
		return iteratorLinkId;
	}
	
	/** Adds Vehicle Circulation Vehicles to Vehicles dataset
	 * 
	 * @param transitVehicles Transit Vehicles dataset of MATSim scenario.
	 * @param mapOfVehicles Map of all created Vehicle Circulation Vehicles
	 */
	static void addTransitVehicles (Vehicles transitVehicles, Map<Id<Vehicle>, VehicleType> mapOfVehicles) {
		for(Id<Vehicle> vehicleId : mapOfVehicles.keySet()) {
			Vehicle vehicle = VehicleUtils.getFactory().createVehicle(vehicleId, mapOfVehicles.get(vehicleId));
			transitVehicles.addVehicle(vehicle);
		}
	}

	/** Connects two Stop Facilities with each other.
	 * 
	 * @param network MATSim Network of Scenario which should be mofied to contain new connecting links
	 * @param startStop first stop of next route which should be served
	 * @param endStop The endstop which should get a connection to the start stop
	 * @param iterator requires an int to create different names of Links
	 * @param mapOfCreatedLinks @TODO
	 * @return iterator that gets injected again
	 */
	static int addLinkBetweenEndAndStart (Network network, TransitRouteStop startStop, TransitRouteStop endStop, int iterator, Map<Id<Link>, Set<Node>> mapOfCreatedLinks) {

		Id<Link> startStopLink = startStop.getStopFacility().getLinkId();
		Id<Link> endStopLink = endStop.getStopFacility().getLinkId();
		
		Node endStopNode = network.getLinks().get(endStopLink).getToNode();
		Node startStopNode = network.getLinks().get(startStopLink).getToNode();
				
		Id<Link> possibleNeededLink = Id.createLinkId("pt_umlauf_" + iterator);
		iterator +=1;
		
		Link linkBetweenEndAndStart = NetworkUtils.createLink(possibleNeededLink, endStopNode, startStopNode, network, 50.0, 10.0, 6000.0, 10.0);
		network.addLink(linkBetweenEndAndStart);
		
		return iterator;
	}
}
