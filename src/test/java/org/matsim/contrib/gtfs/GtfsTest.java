package org.matsim.contrib.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt.utils.CreatePseudoNetwork;

import java.time.LocalDate;

public class GtfsTest {

    @Test
    public void testGtfsStandardConversion() {
        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);
        MutableScenario scenario = (MutableScenario) ScenarioUtils.createScenario(config);

        GtfsConverter gtfs = GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setFeed(GTFSFeed.fromFile("test/input/test-feed.zip"))
                .setTransform(new IdentityTransformation())
                // The WE-Trip is added on July 11th 2011, so calendar.txt and calendar_dates.txt can be checked
                .setDate(LocalDate.of(2011, 7, 11))
                .build();

        gtfs.convert();

        (new TransitScheduleWriter(scenario.getTransitSchedule())).writeFile("avs.xml");

        // The Conversion is done, now read the checked scenario
        MutableScenario checkedScenario = (MutableScenario) (ScenarioUtils.createScenario(config));
        new TransitScheduleReader(checkedScenario).readFile("test/input/transitSchedule.xml");

        this.compareResults(checkedScenario, scenario);
    }

    private void compareResults(MutableScenario expected, MutableScenario actual) {
        this.compareTransitSchedules(expected, actual);
    }

    private void compareTransitSchedules(MutableScenario sc1, MutableScenario sc2) {
        TransitSchedule ts1 = sc1.getTransitSchedule();
        TransitSchedule ts2 = sc2.getTransitSchedule();
        Assertions.assertEquals(ts1.getFacilities().size(), ts2.getFacilities().size());
        for (Id<TransitStopFacility> stopId : ts1.getFacilities().keySet()) {
            Assertions.assertEquals(ts1.getFacilities().get(stopId).getName(), ts2.getFacilities().get(stopId).getName());
            Assertions.assertEquals(ts1.getFacilities().get(stopId).getCoord(), ts2.getFacilities().get(stopId).getCoord());
            Assertions.assertEquals(ts1.getFacilities().get(stopId).getLinkId(), ts2.getFacilities().get(stopId).getLinkId());
            /*
             * TODO: the current gtfs test feed does not contain a parent_station. Modify test input.
             * https://gtfs.org/getting-started/example-feed/ has other example files which do not add up to a full
             * gtfs feed and links a sample feed which lacks the parent_station field.
             */
            Assertions.assertEquals(ts1.getFacilities().get(stopId).getStopAreaId(), ts2.getFacilities().get(stopId).getStopAreaId());
        }
        Assertions.assertEquals(ts1.getTransitLines().size(), ts2.getTransitLines().size());
        for (Id<TransitLine> lineId : ts1.getTransitLines().keySet()) {
            Assertions.assertEquals(ts1.getTransitLines().get(lineId).getRoutes().size(), ts1.getTransitLines().get(lineId).getRoutes().size());
            Assertions.assertEquals(ts1.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_agency_id"), ts2.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_agency_id"));
            Assertions.assertEquals(ts1.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_route_type"), ts2.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_route_type"));
            Assertions.assertEquals(ts1.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_route_short_name"), ts2.getTransitLines().get(lineId).getAttributes().getAttribute("gtfs_route_short_name"));
            Assertions.assertEquals(ts1.getTransitLines().get(lineId).getName(), ts2.getTransitLines().get(lineId).getName());


            for (Id<TransitRoute> routeId : ts1.getTransitLines().get(lineId).getRoutes().keySet()) {
                TransitRoute tr1 = ts1.getTransitLines().get(lineId).getRoutes().get(routeId);
                TransitRoute tr2 = ts2.getTransitLines().get(lineId).getRoutes().get(routeId);
                Assertions.assertEquals(tr1.getStops().size(), tr2.getStops().size());
                Assertions.assertEquals(tr1.getTransportMode(), tr2.getTransportMode());
                for (TransitRouteStop trStop : tr1.getStops()) {
                    Assertions.assertEquals(trStop.isAwaitDepartureTime(), tr2.getStops().get(tr1.getStops().indexOf(trStop)).isAwaitDepartureTime());
                    Assertions.assertEquals(trStop.getDepartureOffset().seconds(), tr2.getStops().get(tr1.getStops().indexOf(trStop)).getDepartureOffset().seconds(), 0.0);
                    Assertions.assertEquals(trStop.getArrivalOffset().seconds(), tr2.getStops().get(tr1.getStops().indexOf(trStop)).getArrivalOffset().seconds(), 0.0);
                }
                Assertions.assertEquals(tr1.getDepartures().size(), tr2.getDepartures().size());
            }
        }
    }

    @Test
    public void testGoogleSample() {
        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);
        MutableScenario scenarioWeekend = (MutableScenario) ScenarioUtils.createScenario(config);

        //Saturday
        GtfsConverter gtfsWeekend = GtfsConverter.newBuilder()
                .setScenario(scenarioWeekend)
                .setTransform(new IdentityTransformation())
                .setFeed(GTFSFeed.fromFile("test/input/sample-feed.zip"))
                .setDate(LocalDate.of(2007, 1, 6))
                .build();

        gtfsWeekend.convert();
        checkSchedule(scenarioWeekend, true);
        int departuresWeekend = scenarioWeekend.getTransitSchedule().getTransitLines().values()
                .stream()
                .flatMap(transitLine -> transitLine.getRoutes().values().stream())
                .mapToInt(r->r.getDepartures().values().size()).sum();
        Assertions.assertEquals(144,departuresWeekend);

        MutableScenario scenarioWeekdays = (MutableScenario) ScenarioUtils.createScenario(config);
        //Monday
        GtfsConverter gtfsWeekdays = GtfsConverter.newBuilder()
                .setScenario(scenarioWeekdays)
                .setTransform(new IdentityTransformation())
                .setFeed(GTFSFeed.fromFile("test/input/sample-feed.zip"))
                .setDate(LocalDate.of(2007, 1, 1))
                .build();

        gtfsWeekdays.convert();
        checkSchedule(scenarioWeekdays, false);
        int departures = scenarioWeekdays.getTransitSchedule().getTransitLines().values()
                .stream()
                .flatMap(transitLine -> transitLine.getRoutes().values().stream())
                .mapToInt(r->r.getDepartures().values().size()).sum();
        Assertions.assertEquals(140,departures);

        MutableScenario scenarioThreeWeekdaysAndTwoWeekendDays = (MutableScenario) ScenarioUtils.createScenario(config);
        //Monday
        GtfsConverter gtfsThreeWeekdaysAndTwoWeekendDays = GtfsConverter.newBuilder()
                .setScenario(scenarioThreeWeekdaysAndTwoWeekendDays)
                .setTransform(new IdentityTransformation())
                .setFeed(GTFSFeed.fromFile("test/input/sample-feed.zip"))
                .setStartDate(LocalDate.of(2007, 1, 3))
                .setEndDate(LocalDate.of(2007,1,7))
                .build();

        gtfsThreeWeekdaysAndTwoWeekendDays.convert();
        int departuresThree = scenarioThreeWeekdaysAndTwoWeekendDays.getTransitSchedule().getTransitLines().values()
                .stream()
                .flatMap(transitLine -> transitLine.getRoutes().values().stream())
                .mapToInt(r->r.getDepartures().values().size()).sum();
        Assertions.assertEquals(3*departures+2*departuresWeekend,departuresThree);

    }


    @Test
    public void testFilterRouteType() {

        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);
        MutableScenario scenario = (MutableScenario) ScenarioUtils.createScenario(config);

        GtfsConverter converter = GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setTransform(new IdentityTransformation())
                .setFeed(GTFSFeed.fromFile("test/input/sample-feed2.zip"))
                .setDate(LocalDate.of(2020, 3, 16))
                .setIncludeRouteType(type -> type == RouteType.TRAM.getCode())
                .build();

        converter.convert();

        Assertions.assertEquals(5, scenario.getTransitSchedule().getTransitLines().size());
    }

    @Test
    public void testFilterAgency() {

        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);
        MutableScenario scenario = (MutableScenario) ScenarioUtils.createScenario(config);

        GtfsConverter converter = GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setTransform(new IdentityTransformation())
                .setFeed(GTFSFeed.fromFile("test/input/sample-feed2.zip"))
                .setDate(LocalDate.of(2020, 3, 16))
                .setIncludeAgency((agency) -> false)
                .build();

        converter.convert();
        Assertions.assertEquals(0, scenario.getTransitSchedule().getTransitLines().size());

    }


    @Test
    public void testFilterStops() {

        Config config = ConfigUtils.createConfig();
        config.transit().setUseTransit(true);
        MutableScenario scenario = (MutableScenario) ScenarioUtils.createScenario(config);

        GtfsConverter converter = GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setTransform(new IdentityTransformation())
                .setFeed(GTFSFeed.fromFile("test/input/sample-feed2.zip"))
                .setDate(LocalDate.of(2020, 3, 16))
                .setIncludeStop(stop -> stop.id % 3 == 0)
                .build();

        converter.convert();

        // Filter 33% of stops and check if still able to create a network
        new CreatePseudoNetwork(scenario.getTransitSchedule(), scenario.getNetwork(), "pt_").createNetwork();

    }

    private void checkSchedule(MutableScenario scenario, boolean weekend) {

        TransitSchedule schedule = scenario.getTransitSchedule();

        int routes = 0;
        for (TransitLine line : schedule.getTransitLines().values()) {
            routes += line.getRoutes().size();
        }

        Assertions.assertEquals(9, schedule.getFacilities().size());

        if (weekend) {
            Assertions.assertEquals(5, schedule.getTransitLines().size());
            //the 4 trips of AAMV line are consolidated into 2. That makes 11 trips/routes decrease to 9.
            Assertions.assertEquals(9, routes);
        } else {
            Assertions.assertEquals(4, schedule.getTransitLines().size());
            Assertions.assertEquals(7, routes);
        }
    }
}
