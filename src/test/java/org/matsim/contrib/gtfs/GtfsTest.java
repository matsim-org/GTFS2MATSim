package org.matsim.contrib.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.time.LocalDate;

public class GtfsTest  {
	
	@Test
	public void testGtfsStandardConversion() {
        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);
		MutableScenario scenario = (MutableScenario) ScenarioUtils.createScenario(config);
		GtfsConverter gtfs = new GtfsConverter(GTFSFeed.fromFile("test/input/test-feed.zip"), scenario, new IdentityTransformation(), false);
		// The WE-Trip is added on July 11th 2011, so calendar.txt and calendar_dates.txt can be checked
		gtfs.setDate(LocalDate.of(2011, 7, 11));
		gtfs.convert();

		// The Conversion is done, now read the checked scenario
		MutableScenario checkedScenario = (MutableScenario)(ScenarioUtils.createScenario(config));
		new TransitScheduleReader(checkedScenario).readFile("test/input/transitSchedule.xml");

		this.compareResults(checkedScenario, scenario);
	}

	private void compareResults(MutableScenario expected, MutableScenario actual){
		this.compareTransitSchedules(expected, actual);
	}

	private void compareTransitSchedules(MutableScenario sc1, MutableScenario sc2) {
		TransitSchedule ts1 = sc1.getTransitSchedule();
		TransitSchedule ts2 = sc2.getTransitSchedule();
		Assert.assertEquals(ts1.getFacilities().size(), ts2.getFacilities().size());
		for(Id<TransitStopFacility> stopId: ts1.getFacilities().keySet()){
			Assert.assertEquals(ts1.getFacilities().get(stopId).getName(), ts2.getFacilities().get(stopId).getName());
			Assert.assertEquals(ts1.getFacilities().get(stopId).getCoord(), ts2.getFacilities().get(stopId).getCoord());
			Assert.assertEquals(ts1.getFacilities().get(stopId).getLinkId(), ts2.getFacilities().get(stopId).getLinkId());
		}
		Assert.assertEquals(ts1.getTransitLines().size(), ts2.getTransitLines().size());
		for(Id<TransitLine> lineId: ts1.getTransitLines().keySet()){
			Assert.assertEquals(ts1.getTransitLines().get(lineId).getRoutes().size(), ts1.getTransitLines().get(lineId).getRoutes().size());
			Assert.assertEquals(ts1.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_agency_id"), ts2.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_agency_id"));
			Assert.assertEquals(ts1.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_route_type"), ts2.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_route_type"));
			Assert.assertEquals(ts1.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_route_short_name"), ts2.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_route_short_name"));
			
			for(Id<TransitRoute> routeId: ts1.getTransitLines().get(lineId).getRoutes().keySet()){
				TransitRoute tr1 = ts1.getTransitLines().get(lineId).getRoutes().get(routeId);
				TransitRoute tr2 = ts2.getTransitLines().get(lineId).getRoutes().get(routeId);
				Assert.assertEquals(tr1.getStops().size(), tr2.getStops().size());
				Assert.assertEquals(tr1.getTransportMode(), tr2.getTransportMode());
				for(TransitRouteStop trStop: tr1.getStops()){
					Assert.assertEquals(trStop.isAwaitDepartureTime(), tr2.getStops().get(tr1.getStops().indexOf(trStop)).isAwaitDepartureTime());
					Assert.assertEquals(trStop.getDepartureOffset(), tr2.getStops().get(tr1.getStops().indexOf(trStop)).getDepartureOffset(), 0.0);
					Assert.assertEquals(trStop.getArrivalOffset(), tr2.getStops().get(tr1.getStops().indexOf(trStop)).getArrivalOffset(), 0.0);
				}
				Assert.assertEquals(tr1.getDepartures().size(), tr2.getDepartures().size());
			}
		}
	}
	
	@Test
	public void testGoogleSample() {
	    	Config config = ConfigUtils.createConfig();
	        config.transit().setUseTransit(true);
		MutableScenario scenarioWeekend = (MutableScenario) ScenarioUtils.createScenario(config);
		MutableScenario scenarioWeekdays = (MutableScenario) ScenarioUtils.createScenario(config);
		
		//Saturday
		GtfsConverter gtfsWeekend = new GtfsConverter(GTFSFeed.fromFile("test/input/sample-feed.zip"), scenarioWeekend, new IdentityTransformation(), false);
		gtfsWeekend.setDate(LocalDate.of(2007, 1, 6));
		gtfsWeekend.convert();
		
		//Monday
		GtfsConverter gtfsWeekdays = new GtfsConverter(GTFSFeed.fromFile("test/input/sample-feed.zip"), scenarioWeekdays, new IdentityTransformation(), false);
		gtfsWeekdays.setDate(LocalDate.of(2007, 1 ,1));
		gtfsWeekdays.convert();
		
		checkSchedule(scenarioWeekdays, false);
		checkSchedule(scenarioWeekend, true);
	}
	
	private void checkSchedule (MutableScenario scenario, boolean weekend) {
	        
	    	TransitSchedule schedule = scenario.getTransitSchedule();
		
		int routes = 0;
		for(TransitLine line: schedule.getTransitLines().values()) {
		    routes += line.getRoutes().size();
		}
		
		Assert.assertEquals(9, schedule.getFacilities().size());
		
		if(weekend) {
		    	Assert.assertEquals(5, schedule.getTransitLines().size());
		    	//the 4 trips of AAMV line are consolidated into 2. That makes 11 trips/routes decrease to 9.
			Assert.assertEquals(9, routes);
		} else {
			Assert.assertEquals(4, schedule.getTransitLines().size());
			Assert.assertEquals(7, routes);
		}
	}
}
