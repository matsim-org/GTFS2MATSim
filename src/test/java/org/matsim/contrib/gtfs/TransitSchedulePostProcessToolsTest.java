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
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class TransitSchedulePostProcessToolsTest {
	
	@Test
	public void testCopyLateDeparturesToStartOfDay() {
		Id<TransitLine> redLineId = Id.create("red", TransitLine.class);
		Id<TransitRoute> redFirstToLastRouteId = Id.create("redFirstToLast", TransitRoute.class);
		
		// without exclusion, copyDespiteArrivalBeforeMidnight=true
		DepartureCopyingFixture f = new DepartureCopyingFixture();
		TransitSchedule schedule = f.schedule;
		TransitSchedulePostProcessTools.copyLateDeparturesToStartOfDay(schedule, 23*3600, null, true);
		Map<Id<Departure>, Departure> departures = 
				schedule.getTransitLines().get(redLineId).getRoutes().get(redFirstToLastRouteId).getDepartures();
		
		Assertions.assertEquals(6, departures.keySet().size(), "wrong number of departures after copying");
		Assertions.assertTrue(oldDeparturesStillExist(departures), "At least one of the old Departures does not exist any longer or has a wrong departure time after copying");
		Assertions.assertEquals(23.0*3600 + 45*60 - 24*3600,
				departures.get(Id.create("copied-24h_lateArrivalBeforeMidnight", Departure.class)).getDepartureTime(), 0.1,
				"Departure lateArrivalBeforeMidnight was not copied or has wrong departure time");
		Assertions.assertEquals(23.0*3600 + 55*60 - 24*3600,
				departures.get(Id.create("copied-24h_lateArrivalAfterMidnight", Departure.class)).getDepartureTime(), 0.1,
				"Departure lateArrivalAfterMidnight was not copied or has wrong departure time");
		
		// without exclusion, copyDespiteArrivalBeforeMidnight=false
		f = new DepartureCopyingFixture();
		schedule = f.schedule;
		TransitSchedulePostProcessTools.copyLateDeparturesToStartOfDay(schedule, 23*3600, null, false);
		departures = schedule.getTransitLines().get(redLineId).getRoutes().get(redFirstToLastRouteId).getDepartures();
		
		Assertions.assertEquals(5, departures.keySet().size(), "wrong number of departures after copying");
		Assertions.assertTrue(oldDeparturesStillExist(departures),
				"At least one of the old Departures does not exist any longer or has a wrong departure time after copying");
		Assertions.assertFalse(departures.containsKey(Id.create("copied-24h_lateArrivalBeforeMidnight", Departure.class)),
				"Departure lateArrivalBeforeMidnight was copied although it arrives before midnight");
		Assertions.assertEquals(23.0*3600 + 55*60 - 24*3600,
				departures.get(Id.create("copied-24h_lateArrivalAfterMidnight", Departure.class)).getDepartureTime(), 0.1,
				"Departure lateArrivalAfterMidnight was not copied or has wrong departure time");
		
		// with exclusion, copyDespiteArrivalBeforeMidnight=true
		f = new DepartureCopyingFixture();
		schedule = f.schedule;
		TransitSchedulePostProcessTools.copyLateDeparturesToStartOfDay(schedule, 23*3600, "After", true);
		departures = schedule.getTransitLines().get(redLineId).getRoutes().get(redFirstToLastRouteId).getDepartures();
		
		Assertions.assertEquals(5, departures.keySet().size(), "wrong number of departures after copying");
		Assertions.assertTrue(oldDeparturesStillExist(departures), "At least one of the old Departures does not exist any longer or has a wrong departure time after copying");
		Assertions.assertEquals(23.0*3600 + 45*60 - 24*3600,
				departures.get(Id.create("copied-24h_lateArrivalBeforeMidnight", Departure.class)).getDepartureTime(), 0.1,
				"Departure lateArrivalBeforeMidnight was not copied or has wrong departure time");
		Assertions.assertFalse(departures.containsKey(Id.create("copied-24h_lateArrivalAfterMidnight", Departure.class)),
				"Departure lateArrivalAfterMidnight was copied although it contains the exclusionMarker");
	}
	
	@Test
	public void testCopyEarlyDeparturesToFollowingNight() {
		Id<TransitLine> redLineId = Id.create("red", TransitLine.class);
		Id<TransitRoute> redFirstToLastRouteId = Id.create("redFirstToLast", TransitRoute.class);
		
		// without exclusion
		DepartureCopyingFixture f = new DepartureCopyingFixture();
		TransitSchedule schedule = f.schedule;
		TransitSchedulePostProcessTools.copyEarlyDeparturesToFollowingNight(schedule, 13*3600, null);
		Map<Id<Departure>, Departure> departures = 
				schedule.getTransitLines().get(redLineId).getRoutes().get(redFirstToLastRouteId).getDepartures();
		
		Assertions.assertEquals(6, departures.keySet().size(), "wrong number of departures after copying");
		Assertions.assertTrue(oldDeparturesStillExist(departures), "At least one of the old Departures does not exist any longer or has a wrong departure time after copying");
		Assertions.assertEquals(6.0*3600 + 24*3600,
				departures.get(Id.create("copied+24h_early", Departure.class)).getDepartureTime(), 0.1,
				"Departure early was not copied or has wrong departure time");
		Assertions.assertEquals(12.0*3600 + 24*3600,
				departures.get(Id.create("copied+24h_midday", Departure.class)).getDepartureTime(), 0.1,
				"Departure midday was not copied or has wrong departure time");
		
		// with exclusion
		f = new DepartureCopyingFixture();
		schedule = f.schedule;
		TransitSchedulePostProcessTools.copyEarlyDeparturesToFollowingNight(schedule, 13*3600, "ear");
		departures = schedule.getTransitLines().get(redLineId).getRoutes().get(redFirstToLastRouteId).getDepartures();
		
		Assertions.assertEquals(5, departures.keySet().size(), "wrong number of departures after copying");
		Assertions.assertTrue(oldDeparturesStillExist(departures), "At least one of the old Departures does not exist any longer or has a wrong departure time after copying");
		Assertions.assertEquals(12.0*3600 + 24*3600,
				departures.get(Id.create("copied+24h_midday", Departure.class)).getDepartureTime(), 0.1,
				"Departure midday was not copied or has wrong departure time");
		Assertions.assertFalse(departures.containsKey(Id.create("copied+24h_early", Departure.class)),
				"Departure early was copied although it contains the exclusionMarker");
	}
	
	private boolean oldDeparturesStillExist(Map<Id<Departure>, Departure> departures)  {
		if (departures.containsKey(Id.create("early", Departure.class))
				&& departures.containsKey(Id.create("midday", Departure.class))
				&& departures.containsKey(Id.create("lateArrivalBeforeMidnight", Departure.class))
				&& departures.containsKey(Id.create("lateArrivalAfterMidnight", Departure.class))
				&& Double.valueOf (6.0*3600).equals(departures.get(Id.create("early", Departure.class)).getDepartureTime())
				&& Double.valueOf (12.0*3600).equals(departures.get(Id.create("midday", Departure.class)).getDepartureTime())
				&& Double.valueOf (23.0*3600 + 45*60).equals(departures.get(Id.create("lateArrivalBeforeMidnight", Departure.class)).getDepartureTime())
				&& Double.valueOf (23.0*3600 + 55*60).equals(departures.get(Id.create("lateArrivalAfterMidnight", Departure.class)).getDepartureTime())) {
					return true;
				} else {
					return false;
				}
	}

    private static class DepartureCopyingFixture {
    	TransitSchedule schedule;
    	
    	public DepartureCopyingFixture() {
            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            schedule = scenario.getTransitSchedule();
            TransitScheduleFactory sf = schedule.getFactory();
            
            TransitStopFacility firstStop = sf.createTransitStopFacility(Id.create("A", TransitStopFacility.class), CoordUtils.createCoord(0, 0), false);
            TransitStopFacility lastStop = sf.createTransitStopFacility(Id.create("B", TransitStopFacility.class), CoordUtils.createCoord(10000, 0), false);
            
            Id<Link> firstStopLinkId = Id.createLinkId("link_firstStop");
            Id<Link> lastStopLinkId = Id.createLinkId("link_lastStop");
            
            firstStop.setLinkId(firstStopLinkId);
            lastStop.setLinkId(lastStopLinkId);
            
            schedule.addStopFacility(firstStop);
            schedule.addStopFacility(lastStop);
            
            TransitLine redLine = sf.createTransitLine(Id.create("red", TransitLine.class));

            NetworkRoute networkRoute = RouteUtils.createLinkNetworkRouteImpl(firstStopLinkId, new ArrayList<Id<Link>>(), lastStopLinkId);
            List<TransitRouteStop> stopsRed = new ArrayList<>(2);
            stopsRed.add(sf.createTransitRouteStopBuilder(firstStop).departureOffset(0.0).build());
            stopsRed.add(sf.createTransitRouteStopBuilder(lastStop).arrivalOffset(600).build());
            TransitRoute redABRoute = sf.createTransitRoute(Id.create("redFirstToLast", TransitRoute.class), networkRoute, stopsRed, "hoovercraft");
            redABRoute.addDeparture(sf.createDeparture(Id.create("early", Departure.class), 6.0*3600));
            redABRoute.addDeparture(sf.createDeparture(Id.create("midday", Departure.class), 12.0*3600));
            redABRoute.addDeparture(sf.createDeparture(Id.create("lateArrivalBeforeMidnight", Departure.class), 23.0*3600 + 45*60));
            redABRoute.addDeparture(sf.createDeparture(Id.create("lateArrivalAfterMidnight", Departure.class), 23.0*3600 + 55*60));
            redLine.addRoute(redABRoute);

            schedule.addTransitLine(redLine);
    	}
    }
	
}
