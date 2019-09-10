/* *********************************************************************** *
 * project: org.matsim.contrib.gtfs.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.gtfs;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * 
 * TODO: Test
 * 
 * @author vsp-gleich
 *
 */
public class PostProcessTools {
	
	/**
	 * Sometimes departures of day x are found in GTFS data as a trip on day x-1 at 24:00 hours or later.
	 * For simplicity we don't look up the previous day in gtfs, but simply copy Departures from these late hours
	 * (departure at first stop at or after @param startTimeOfCopying) and re-insert them 24 hours earlier.
	 * 
	 * @param schedule TransitSchedule
	 * @param startTimeOfCopying starting at that time of the converted day all trips are copied and 
	 * added 24 hours earlier to the same day
	 * @param departureExclusionMarker if departure id contains this String, it won't be copied, 
	 * e.g. useful if {@link copyEarlyDeparturesToFollowingNight} was run before
	 * @param copyDespiteArrivalBeforeMidnight if startTimeOfCopying < 24:00 it might happen that a Departure is copied 
	 * although it arrives at the terminus already before midnight. If copied, it would arrive at the terminus stop before
	 * time 0:00 and for that reason would be useless for normal Matsim agents departing at 0:00 or after. 
	 * To avoid copying such Departures, set the param to false.
	 */
	public static void copyLateDeparturesToStartOfDay(TransitSchedule schedule, double startTimeOfCopying, 
			String departureExclusionMarker, boolean copyDespiteArrivalBeforeMidnight) {
		for (TransitLine line: schedule.getTransitLines().values()) {
			for (TransitRoute route: line.getRoutes().values()) {
				List<Departure> departuresToBeAdded = new ArrayList<>();
				
				for (Departure dep: route.getDepartures().values()) {
					double oldDepartureTime = dep.getDepartureTime();
					// do not copy Departures which arrive before midnight ()
					double arrivalAtLastStop = dep.getDepartureTime() + route.getStops().get(route.getStops().size() - 1).getArrivalOffset();
					if (oldDepartureTime > startTimeOfCopying && 
							!dep.getId().toString().contains(departureExclusionMarker) &&
							(copyDespiteArrivalBeforeMidnight || arrivalAtLastStop >= 24*3600) ) {
						Departure copiedDep = schedule.getFactory().createDeparture(
								Id.create("copied-24h_" + dep.getId().toString(), Departure.class), 
								oldDepartureTime - 24*3600);
						departuresToBeAdded.add(copiedDep);
					}
				}
				
				for (Departure copiedDep: departuresToBeAdded) {
					route.addDeparture(copiedDep);
				}
			}
		}
	}
	
	/**
	 * For agents using pt after midnight we have to copy some departures from the night before.
	 * For simplicity we don't look up the folowing day in gtfs, but simply copy Departures from these early hours
	 * (departure at first stop until @param endTimeOfCopying) and re-insert them 24 hours later.
	 * 
	 * @param schedule TransitSchedule
	 * @param endTimeOfCopying until that time of the converted day all trips are copied and 
	 * added 24 hours later to the same day
	 * @param departureExclusionMarker if departure id contains this String, it won't be copied, 
	 * e.g. useful if {@link copyLateDeparturesToStartOfDay} was run before
	 */
	public static void copyEarlyDeparturesToFollowingNight(TransitSchedule schedule, double endTimeOfCopying, 
			String departureExclusionMarker) {
		for (TransitLine line: schedule.getTransitLines().values()) {
			for (TransitRoute route: line.getRoutes().values()) {
				List<Departure> departuresToBeAdded = new ArrayList<>();
				
				for (Departure dep: route.getDepartures().values()) {
					double oldDepartureTime = dep.getDepartureTime();
					if (oldDepartureTime < endTimeOfCopying && 
							!dep.getId().toString().contains(departureExclusionMarker)) {
						Departure copiedDep = schedule.getFactory().createDeparture(
								Id.create("copied+24h_" + dep.getId().toString(), Departure.class), 
								oldDepartureTime + 24*3600);
						departuresToBeAdded.add(copiedDep);
					}
				}
				
				for (Departure copiedDep: departuresToBeAdded) {
					route.addDeparture(copiedDep);
				}
			}
		}
	}

}
