package org.matsim.contrib.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.*;

import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GtfsConverter {

    private static final Logger log = LogManager.getLogger(GtfsConverter.class);

    private final GTFSFeed feed;
    private final CoordinateTransformation transform;
    private final TransitSchedule ts;
    private final Predicate<Trip> includeTrip;
    private final Predicate<Stop> includeStop;
    private final Predicate<String> includeAgency;
    private final Predicate<Integer> includeRouteType;
    private final boolean useExtendedRouteTypes;
    private final boolean mergeStops;


    private LocalDate date = LocalDate.now();

    /**
     * Stop that that have been mapped to the same facility.
     */
    private final Map<String, Id<TransitStopFacility>> mappedStops = new HashMap<>();

    /**
     * Constructor.
     *
     * @deprecated Use {@link #newBuilder()} instead
     */
    @Deprecated
    public GtfsConverter(GTFSFeed feed, Scenario scenario, CoordinateTransformation transform, boolean useExtendedRouteTypes) {
        this.feed = Objects.requireNonNull(feed, "Gtfs feed is required");
        this.transform = Objects.requireNonNull(transform, "Coordinate transformation is required");
        this.ts = scenario.getTransitSchedule();
        this.useExtendedRouteTypes = useExtendedRouteTypes;
        this.includeTrip = (t) -> true;
        this.includeStop = (t) -> true;
        this.includeAgency = (t) -> true;
        this.includeRouteType = (t) -> true;
        this.mergeStops = false;
    }

    private GtfsConverter(GTFSFeed feed, CoordinateTransformation transform, Scenario scenario, LocalDate date, boolean useExtendedRouteTypes,
            Predicate<Trip> includeTrips, Predicate<Stop> includeStops, Predicate<String> includeAgency, Predicate<Integer> includeRouteType,
            boolean mergeStops, LocalDate startDate, LocalDate endDate) {
        this.feed = Objects.requireNonNull(feed, "Gtfs feed is required, use .setFeed(...)");
        this.transform = Objects.requireNonNull(transform, "Coordinate transformation is required, use .setTransform(...)");
        this.ts = Objects.requireNonNull(scenario, "Scenario is required, use .setScenario(...)").getTransitSchedule();
        this.date = date;
        this.useExtendedRouteTypes = useExtendedRouteTypes;
        this.includeTrip = includeTrips;
        this.includeStop = includeStops;
        this.includeAgency = includeAgency;
        this.includeRouteType = includeRouteType;
        this.mergeStops = mergeStops;
    }

    /**
     * @see #newBuilder() to set the date.
     */
    @Deprecated
    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void convert() {

        // Put all stops in the Schedule
        this.convertStops();

        LocalDate startDate = LocalDate.MAX;
        for (Service service : this.feed.services.values()) {
            if (service.calendar != null && service.calendar.start_date.isBefore(startDate)) {
                startDate = service.calendar.start_date;
            }
            if (service.calendar_dates != null) {
                for (LocalDate exceptionDate : service.calendar_dates.keySet()) {
                    if (exceptionDate.isBefore(startDate)) {
                        startDate = exceptionDate;
                    }
                }
            }
        }

        log.info("Earliest date mentioned in feed: " + startDate);

        LocalDate endDate = LocalDate.MIN;
        for (Service service : this.feed.services.values()) {
            if (service.calendar != null && service.calendar.end_date.isAfter(endDate)) {
                endDate = service.calendar.end_date;
            }
            if (service.calendar_dates != null) {
                for (LocalDate exceptionDate : service.calendar_dates.keySet()) {
                    if (exceptionDate.isAfter(endDate)) {
                        endDate = exceptionDate;
                    }
                }
            }

        }
        log.info("Latest date mentioned in feed: " + endDate);

        // Get the used service Id for the chosen weekday and date
        List<String> activeServiceIds = this.getActiveServiceIds(this.feed.services);
        log.info(String.format("Active Services: %d %s", activeServiceIds.size(), activeServiceIds));

        // Get the Trips which are active today
        List<Trip> activeTrips = feed.trips.values().stream()
                .filter(trip -> feed.services.get(trip.service_id).activeOn(this.date))
                .filter(this.includeTrip)
                .filter(this::filterAgencyAndType)
                .collect(Collectors.toList());

        log.info(String.format("Active Trips: %d %s", activeTrips.size(), activeTrips.stream().map(trip -> trip.trip_id).collect(Collectors.toList())));

        // Create one TransitLine for each GTFS-Route which has an active trip
        activeTrips.stream().map(trip -> feed.routes.get(trip.route_id)).distinct().forEach(route -> {
            TransitLine tl = ts.getFactory().createTransitLine(getReadableTransitLineId(route));
            ts.addTransitLine(tl);
            if (route.agency_id != null) tl.getAttributes().putAttribute("gtfs_agency_id", String.valueOf(route.agency_id));
            tl.getAttributes().putAttribute("gtfs_route_type", String.valueOf(route.route_type)); // route type is a required field according to GTFS specification
            String routeShortName = null;
            if (route.route_short_name != null) {
                routeShortName = route.route_short_name;
            } else {
                // use id in case there is no route short name
                routeShortName = String.valueOf(route.route_id);
            }
            tl.getAttributes().putAttribute("gtfs_route_short_name",
                    Normalizer.normalize(routeShortName, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "")); // replaces non ascii symbols
        });

        this.convertTrips(activeTrips);

        if (activeTrips.isEmpty()) {
            log.warn("There are no converted trips. You might need to change the date for better results.");
        }
        log.info("Conversion successful");
    }

    private boolean filterAgencyAndType(Trip trip) {
        Route route = feed.routes.get(trip.route_id);
        return includeRouteType.test(route.route_type) && includeAgency.test(route.agency_id);
    }

    private void convertStops() {

        Map<Coord, Id<TransitStopFacility>> coords = new HashMap<>();

        for (Stop stop : feed.stops.values()) {
            if (!includeStop.test(stop))
                continue;

            Coord coord = this.transform.transform(new Coord(stop.stop_lon, stop.stop_lat));

            // Already have a stop with same coord
            if (mergeStops && coords.containsKey(coord)) {
                mappedStops.put(stop.stop_id, coords.get(coord));
                continue;
            }

            TransitStopFacility t = this.ts.getFactory().createTransitStopFacility(Id.create(stop.stop_id, TransitStopFacility.class), coord, false);
            t.setName(stop.stop_name);

            // add only if not yet present
            if (!ts.getFacilities().containsKey(t.getId()))
               ts.addStopFacility(t);

            coords.put(coord, t.getId());
        }
    }


    private List<String> getActiveServiceIds(Map<String, Service> services) {
        List<String> serviceIds = new ArrayList<>();
        log.info("Used Date for active schedules: " + this.date.toString() + " (weekday: " + date.getDayOfWeek().toString() + "). If you want to choose another date, please specify it, before running the converter");
        for (Service service : services.values()) {
            if (service.activeOn(date)) {
                serviceIds.add(service.service_id);
            }
        }
        return serviceIds;
    }


    private void convertTrips(List<Trip> trips) {
        int scheduleDepartures = 0;
        int frequencyDepartures = 0;
        for (Trip trip : trips) {
            if (feed.getFrequencies(trip.trip_id).isEmpty()) {
                if (feed.getOrderedStopTimesForTrip(trip.trip_id) == null || !feed.getOrderedStopTimesForTrip(trip.trip_id).iterator().hasNext()) {
                    log.error("Found a trip with neither frequency nor ordered stop times. Will not add any Matsim TransitRoute/Departure for that trip. GTFS trip_id=" + trip.trip_id);
                    continue;
                }
                StopTime firstStopTime = feed.getOrderedStopTimesForTrip(trip.trip_id).iterator().next();
                Double departureTime = Time.parseTime(String.valueOf(firstStopTime.departure_time));
                List<TransitRouteStop> stops = new ArrayList<>();
                try {
                    for (StopTime stopTime : feed.getInterpolatedStopTimesForTrip(trip.trip_id)) {
                        Id<TransitStopFacility> stopId = findTransitStop(stopTime);
                        TransitStopFacility stop = ts.getFacilities().get(stopId);

                        // This stop was filtered and will be ignored
                        if (stop == null)
                            continue;

						TransitRouteStop.Builder builder = ts.getFactory().createTransitRouteStopBuilder(stop);
						if (stopTime.arrival_time != Integer.MIN_VALUE) {
							double arrivalOffset = Time.parseTime(String.valueOf(stopTime.arrival_time)) - departureTime;
							builder.arrivalOffset(arrivalOffset);
						}
						if (stopTime.departure_time != Integer.MIN_VALUE) {
							double departureOffset = Time.parseTime(String.valueOf(stopTime.departure_time)) - departureTime;
							builder.departureOffset(departureOffset);
						}
						TransitRouteStop routeStop = builder.build();
						routeStop.setAwaitDepartureTime(true);
						stops.add(routeStop);
                    }
                } catch (GTFSFeed.FirstAndLastStopsDoNotHaveTimes firstAndLastStopsDoNotHaveTimes) {
                    throw new RuntimeException(firstAndLastStopsDoNotHaveTimes);
                }
                TransitLine tl = ts.getTransitLines().get(getReadableTransitLineId(trip));
                TransitRoute tr = findOrAddTransitRoute(tl, feed.routes.get(trip.route_id), stops);
                Departure departure = ts.getFactory().createDeparture(Id.create(trip.trip_id, Departure.class), departureTime);
                tr.addDeparture(departure);
                scheduleDepartures++;
            } else {
                List<TransitRouteStop> stops = new ArrayList<>();
                for (StopTime stopTime : feed.getOrderedStopTimesForTrip(trip.trip_id)) {
                    Id<TransitStopFacility> stopId = findTransitStop(stopTime);
                    TransitStopFacility stop = ts.getFacilities().get(stopId);

                    if (stop == null)
                        continue;

                    TransitRouteStop routeStop = ts.getFactory().createTransitRouteStop(stop, Time.parseTime(String.valueOf(stopTime.arrival_time)), Time.parseTime(String.valueOf(stopTime.departure_time)));
                    // transit drivers should always await departure, because otherwise they can run far ahead of schedule
                    routeStop.setAwaitDepartureTime(true);
                    stops.add(routeStop);
                }
                for (Frequency frequency : feed.getFrequencies(trip.trip_id)) {
                    for (int time = frequency.start_time; time < frequency.end_time; time += frequency.headway_secs) {
                        TransitLine tl = ts.getTransitLines().get(getReadableTransitLineId(trip));
                        TransitRoute tr = findOrAddTransitRoute(tl, feed.routes.get(trip.route_id), stops);
                        Departure d = ts.getFactory().createDeparture(Id.create(trip.trip_id + "." + time, Departure.class), time);
                        tr.addDeparture(d);
                        frequencyDepartures++;
                    }
                }
            }
        }
        log.info("Created schedule-based departures: " + scheduleDepartures);
        log.info("Created frequency-based departures: " + frequencyDepartures);
    }

    private Id<TransitStopFacility> findTransitStop(StopTime stopTime) {
        if (!mergeStops || !mappedStops.containsKey(stopTime.stop_id))
            return Id.create(stopTime.stop_id, TransitStopFacility.class);

        return mappedStops.get(stopTime.stop_id);
    }


    private TransitRoute findOrAddTransitRoute(TransitLine tl, Route route, List<TransitRouteStop> stops) {
        for (TransitRoute tr : tl.getRoutes().values()) {
            if (tr.getStops().equals(stops)) {
                return tr;
            }
        }
        Id<TransitRoute> routeId = Id.create(tl.getId().toString() + "_" + tl.getRoutes().size(), TransitRoute.class);

        RouteType routeType = RouteType.getRouteTypes().get(route.route_type);
        if (routeType == null) {
            throw new RuntimeException("This route type does not exist! Route type = " + route.route_type);
        }
        TransitRoute tr = null;
        if (!useExtendedRouteTypes) {
            tr = ts.getFactory().createTransitRoute(routeId, /*networkRoute*/ null, stops, routeType.getSimpleTypeName());
        } else {
            tr = ts.getFactory().createTransitRoute(routeId, /*networkRoute*/ null, stops, routeType.getTypeName());
        }
        tl.addRoute(tr);
        return tr;
    }

    private Id<TransitLine> getReadableTransitLineId(Trip trip) {
        return getReadableTransitLineId(feed.routes.get(trip.route_id));
    }

    private Id<TransitLine> getReadableTransitLineId(Route route) {
        String asciiShortName = "XXX";
        if (route.route_short_name != null && route.route_short_name.length() > 0) {
            asciiShortName = Normalizer.normalize(route.route_short_name, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
        }
        return Id.create(asciiShortName + "---" + route.route_id, TransitLine.class);
    }

    /**
     * Creates a new builder for setting converter parameters.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private GTFSFeed feed;
        private CoordinateTransformation transform;
        private LocalDate date = LocalDate.now();
        private boolean useExtendedRouteTypes = false;
        private boolean mergeStops = false;
        private Scenario scenario;
        private Predicate<Trip> includeTrip = (t) -> true;
        private Predicate<Stop> includeStop = (t) -> true;
        private Predicate<String> includeAgency = (t) -> true;
        private Predicate<Integer> includeRouteType = (t) -> true;
        private LocalDate startDate;
        private LocalDate endDate;

        private Builder() {
        }

        /**
         * Creates the converter instance.
         */
        public GtfsConverter build() {
            return new GtfsConverter(feed, transform, scenario, date, useExtendedRouteTypes,
                    includeTrip, includeStop, includeAgency, includeRouteType, mergeStops, startDate, endDate);
        }

        /**
         * Sets the GTFS feed from which to extract the schedules.
         */
        public Builder setFeed(GTFSFeed feed) {
            this.feed = feed;
            return this;
        }

        /**
         * @see #setFeed(GTFSFeed)
         */
        public Builder setFeed(Path feed) {
            this.feed = GTFSFeed.fromFile(feed.toString());
            return this;
        }

        /**
         * Required coordinate transformation.
         */
        public Builder setTransform(CoordinateTransformation transform) {
            this.transform = transform;
            return this;
        }

        /**
         * Day on which the schedules will be extracted.
         */
        public Builder setDate(LocalDate date) {
            this.date = date;
            return this;
        }

        /**
         * Start date from which the schedules will be extracted.
         */
        public Builder setStartDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        /**
         * End date until which the schedules will be extracted.
         */
        public Builder setEndDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }



        /**
         * Conversion result will be inserted into this scenario.
         */
        public Builder setScenario(Scenario scenario) {
            this.scenario = scenario;
            return this;
        }

        /**
         * Predicate for filtering {@link Trip}.
         */
        public Builder setIncludeTrip(Predicate<Trip> includeTrip) {
            this.includeTrip = includeTrip;
            return this;
        }

        /**
         * Filter to check if a trip by a certain agency should be included.
         */
        public Builder setIncludeAgency(Predicate<String> includeAgency) {
            this.includeAgency = includeAgency;
            return this;
        }

        /**
         * Filter to check if a trip with certain {@link RouteType} should be included.
         */
        public Builder setIncludeRouteType(Predicate<Integer> includeRouteType) {
            this.includeRouteType = includeRouteType;
            return this;
        }

        /**
         * Filter to check if {@link Stop} should be included in the schedule.
         */
        public Builder setIncludeStop(Predicate<Stop> includeStop) {
            this.includeStop = includeStop;
            return this;
        }

        public Builder setUseExtendedRouteTypes(boolean useExtendedRouteTypes) {
            this.useExtendedRouteTypes = useExtendedRouteTypes;
            return this;
        }

        /**
         * Merge stops on the same coordinate.
         */
        public Builder setMergeStops(boolean mergeStops) {
            this.mergeStops = mergeStops;
            return this;
        }
    }

}
