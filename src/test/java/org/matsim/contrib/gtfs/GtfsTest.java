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
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.time.LocalDate;

public class GtfsTest  {
	
	@Test
	public void testGtfsStandardConversion() {
        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);
		MutableScenario scenario = (MutableScenario) ScenarioUtils.createScenario(config);
		GtfsConverter gtfs = new GtfsConverter(GTFSFeed.fromFile("test/input/test-feed.zip"), scenario, new IdentityTransformation());
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
		for(Id stopId: ts1.getFacilities().keySet()){
			Assert.assertEquals(ts1.getFacilities().get(stopId).getName(), ts2.getFacilities().get(stopId).getName());
			Assert.assertEquals(ts1.getFacilities().get(stopId).getCoord(), ts2.getFacilities().get(stopId).getCoord());
			Assert.assertEquals(ts1.getFacilities().get(stopId).getLinkId(), ts2.getFacilities().get(stopId).getLinkId());
		}
		Assert.assertEquals(ts1.getTransitLines().size(), ts2.getTransitLines().size());
		for(Id transitId: ts1.getTransitLines().keySet()){
			Assert.assertEquals(ts1.getTransitLines().get(transitId).getRoutes().size(), ts1.getTransitLines().get(transitId).getRoutes().size());
			for(Id routeId: ts1.getTransitLines().get(transitId).getRoutes().keySet()){
				TransitRoute tr1 = ts1.getTransitLines().get(transitId).getRoutes().get(routeId);
				TransitRoute tr2 = ts2.getTransitLines().get(transitId).getRoutes().get(routeId);
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

}
