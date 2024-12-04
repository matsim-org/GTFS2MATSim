package org.matsim.contrib.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.*;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.*;

import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GtfsConverter {

    private static final Logger log = LogManager.getLogger(GtfsConverter.class);

    private final GTFSFeed feed;
    private final CoordinateTransformation transform;
    private final TransitSchedule ts;
    private final Predicate<Trip> includeTrip;
    private final Consumer<Stop> transformStop;
    private final Consumer<Route> transformRoute;
    private final Predicate<Stop> includeStop;
    private final Predicate<String> includeAgency;
    private final Predicate<Integer> includeRouteType;
    private final boolean useExtendedRouteTypes;
    private final MergeGtfsStops mergeStops;
    private final boolean includeMinimalTransferTimes;
    private final String prefix;
    /**
     * Stop that have been mapped to the same facility.
     */
    private final Map<String, Id<TransitStopFacility>> mappedStops = new HashMap<>();
    private LocalDate endDate;
    private LocalDate startDate;

    private GtfsConverter(Builder builder) {
        this.feed = Objects.requireNonNull(builder.feed, "Gtfs feed is required, use .setFeed(...)");
        this.transform = Objects.requireNonNull(builder.transform, "Coordinate transformation is required, use .setTransform(...)");
        this.ts = Objects.requireNonNull(builder.scenario, "Scenario is required, use .setScenario(...)").getTransitSchedule();
        this.startDate = builder.startDate;
        this.useExtendedRouteTypes = builder.useExtendedRouteTypes;
        this.includeTrip = builder.includeTrip;
        this.transformStop = builder.transformStop;
        this.transformRoute = builder.transformRoute;
        this.includeStop = builder.includeStop;
        this.includeAgency = builder.includeAgency;
        this.includeRouteType = builder.includeRouteType;
        this.mergeStops = builder.mergeStops;
        this.includeMinimalTransferTimes = builder.includeMinimalTransferTimes;
        this.prefix = builder.prefix;
        this.endDate = builder.endDate;
        if (builder.endDate == null && builder.startDate == null & builder.date != null) {
            this.startDate = builder.date;
            this.endDate = builder.date;
        }
        if (this.endDate != null && this.startDate != null && this.endDate.compareTo(this.startDate) < 0) {
            throw new RuntimeException("Start Date " + startDate + " larger than End date " + endDate);
        }
    }

    /**
     * Creates a new builder for setting converter parameters.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    public void convert() {

        if (transformRoute != null) {
            List<Map.Entry<String, Route>> routes = feed.routes.entrySet().stream().toList();
            // Values are transformed and put back into the map so that the information is updated
            for (Map.Entry<String, Route> e : routes) {
                transformRoute.accept(e.getValue());
                feed.routes.put(e.getKey(), e.getValue());
            }
        }

        // Put all stops in the Schedule
        this.convertStops();

        if (this.includeMinimalTransferTimes) {
            this.convertTransferTimes();
        }

        LocalDate feedStartDate = LocalDate.MAX;
        for (Service service : this.feed.services.values()) {
            if (service.calendar != null && service.calendar.start_date.isBefore(feedStartDate)) {
                feedStartDate = service.calendar.start_date;
            }
            if (service.calendar_dates != null) {
                for (LocalDate exceptionDate : service.calendar_dates.keySet()) {
                    if (exceptionDate.isBefore(feedStartDate)) {
                        feedStartDate = exceptionDate;
                    }
                }
            }
        }

        log.info("Earliest date mentioned in feed: " + feedStartDate);

        LocalDate feedEndDate = LocalDate.MIN;
        for (Service service : this.feed.services.values()) {
            if (service.calendar != null && service.calendar.end_date.isAfter(feedEndDate)) {
                feedEndDate = service.calendar.end_date;
            }
            if (service.calendar_dates != null) {
                for (LocalDate exceptionDate : service.calendar_dates.keySet()) {
                    if (exceptionDate.isAfter(feedEndDate)) {
                        feedEndDate = exceptionDate;
                    }
                }
            }

        }
        log.info("Latest date mentioned in feed: " + feedEndDate);
        ts.getAttributes().putAttribute("startDate", startDate.toString());
        ts.getAttributes().putAttribute("endDate", endDate.toString());
        LocalDate date = startDate;
        int offsetDays = 0;
        do {
            // Get the used service Id for the chosen weekday and date
            List<String> activeServiceIds = this.getActiveServiceIds(this.feed.services, date);
            log.info("Active Services: " + activeServiceIds.size());

            // Get the Trips which are active today
            final LocalDate finalDate = date;
            List<Trip> activeTrips = feed.trips.values().stream()
                    .filter(trip -> feed.services.get(trip.service_id).activeOn(finalDate))
                    .filter(this.includeTrip)
                    .filter(this::filterAgencyAndType)
                    .collect(Collectors.toList());

            // Create one TransitLine for each GTFS-Route which has an active trip
            activeTrips.stream().map(trip -> feed.routes.get(trip.route_id)).distinct().forEach(route -> {
                TransitLine tl = ts.getFactory().createTransitLine(getReadableTransitLineId(route));
                if (!ts.getTransitLines().containsKey(tl.getId())) {
                    ts.addTransitLine(tl);
                    if (route.agency_id != null)
                        tl.getAttributes().putAttribute("gtfs_agency_id", String.valueOf(route.agency_id));
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
                    tl.setName(routeShortName);
                }
            });

            this.convertTrips(activeTrips, offsetDays);
            date = date.plusDays(1);
            offsetDays++;
            if (activeTrips.isEmpty()) {
                log.warn("There are no converted trips. You might need to change the date for better results.");
            }
        } while (!date.isEqual(this.endDate.plusDays(1)));

        log.info("Conversion successful");
    }

    private boolean filterAgencyAndType(Trip trip) {
        Route route = feed.routes.get(trip.route_id);
        return includeRouteType.test(route.route_type) && includeAgency.test(route.agency_id);
    }

    private void convertStops() {

        Map<Coord, Id<TransitStopFacility>> coords = new HashMap<>();

        // Used for mapping stops and route types identifier
        Map<String, String> routeTypes = null;
        if (mergeStops == MergeGtfsStops.mergeToParentAndRouteTypes) {
            routeTypes = buildRouteTypes();
        }

        for (Stop stop : feed.stops.values()) {

            if (transformStop != null) {
                transformStop.accept(stop);
            }

            if (!includeStop.test(stop))
                continue;

            Coord coord = CoordUtils.round(this.transform.transform(new Coord(stop.stop_lon, stop.stop_lat)));
            Id<TransitStopFacility> id = getMatsimTransitStopIdFromGtfsStopId(stop.stop_id);

            // Already have a stop with same coord
            if (mergeStops.equals(MergeGtfsStops.mergeStopsAtSameCoord) && coords.containsKey(coord)) {
                mappedStops.put(stop.stop_id, coords.get(coord));
                continue;
            }
            if (mergeStops.equals(MergeGtfsStops.mergeToGtfsParentStation) && stop.parent_station != null) {
                mappedStops.put(stop.stop_id, getMatsimTransitStopIdFromGtfsStopId(stop.parent_station));
                continue;
            }

            if (routeTypes != null && routeTypes.containsKey(stop.stop_id)) {
                id = getMatsimTransitStopIdFromGtfsStopId(routeTypes.get(stop.stop_id));
                mappedStops.put(stop.stop_id, id);

                // Need to check if facility was already created.
                if (ts.getFacilities().containsKey(id)) {
                    continue;
                }
            }

            TransitStopFacility t = this.ts.getFactory().createTransitStopFacility(id, coord, false);

            Pattern pattern = java.util.regex.Pattern.compile("\\p{C}", Pattern.CASE_INSENSITIVE);

            if (pattern.matcher(stop.stop_name).find()) {
                String cleaned = stop.stop_name.replaceAll("\\p{C}", "");
                t.setName(cleaned);
            } else
                t.setName(stop.stop_name);

            // add only if not yet present
            if (!ts.getFacilities().containsKey(t.getId()))
                ts.addStopFacility(t);

            if (stop.parent_station != null && !stop.parent_station.isEmpty()) {
                t.setStopAreaId(Id.create(stop.parent_station, TransitStopArea.class));
            }

            coords.put(coord, t.getId());
        }
    }

    /**
     * Collect all route types that go through a certain stop. Only collected for these with existing parent ids.
     */
    private Map<String, String> buildRouteTypes() {

        Map<String, IntSet> routeTypes = new HashMap<>();

        for (Trip trip : feed.trips.values()) {
            Route route = feed.routes.get(trip.route_id);

            for (StopTime stopTime : feed.getOrderedStopTimesForTrip(trip.trip_id)) {
                routeTypes.computeIfAbsent(stopTime.stop_id, k -> new IntLinkedOpenHashSet()).add(route.route_type);
            }
        }

        Map<String, String> result = new HashMap<>();

        // Build the resulting identifiers
        for (Stop stop : feed.stops.values()) {
            if (stop.parent_station == null)
                continue;

            result.put(stop.stop_id, stop.parent_station + "_" + routeTypes.getOrDefault(stop.stop_id, IntSet.of()).intStream()
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining("_")));
        }

        return result;
    }

    private Id<TransitStopFacility> getMatsimTransitStopIdFromGtfsStopId(String stopId) {
        return Id.create(prefix + stopId, TransitStopFacility.class);
    }


    private void convertTransferTimes() {
        for (Transfer transfer : this.feed.transfers.values()) {
            Id<TransitStopFacility> fromStop = findTransitStop(transfer.from_stop_id);
            Id<TransitStopFacility> tostop = findTransitStop(transfer.to_stop_id);
            if (fromStop != null && tostop != null) {
                this.ts.getMinimalTransferTimes().set(fromStop, tostop, transfer.min_transfer_time);
            }
        }
    }


    private List<String> getActiveServiceIds(Map<String, Service> services, LocalDate date) {
        List<String> serviceIds = new ArrayList<>();
        log.info("Used Date for active schedules: " + date.toString() + " (weekday: " + date.getDayOfWeek().toString() + "). If you want to choose another date, please specify it, before running the converter");
        for (Service service : services.values()) {
            if (service.activeOn(date)) {
                serviceIds.add(service.service_id);
            }
        }
        return serviceIds;
    }


    private void convertTrips(List<Trip> trips, int offsetDays) {
        int scheduleDepartures = 0;
        int frequencyDepartures = 0;
        int offset = offsetDays * 24 * 3600;
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
                        Id<TransitStopFacility> stopId = findTransitStop(stopTime.stop_id);
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
                Departure departure = ts.getFactory().createDeparture(Id.create(prefix + trip.trip_id + "_" + offset, Departure.class), departureTime + offset);
                tr.addDeparture(departure);
                scheduleDepartures++;
            } else {
                List<TransitRouteStop> stops = new ArrayList<>();
                for (StopTime stopTime : feed.getOrderedStopTimesForTrip(trip.trip_id)) {
                    Id<TransitStopFacility> stopId = findTransitStop(stopTime.stop_id);
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
                        Departure d = ts.getFactory().createDeparture(Id.create(prefix + trip.trip_id + "." + time + offset, Departure.class), time + offset);
                        tr.addDeparture(d);
                        frequencyDepartures++;
                    }
                }
            }
        }
        log.info("Created schedule-based departures: " + scheduleDepartures);
        log.info("Created frequency-based departures: " + frequencyDepartures);
    }

    private Id<TransitStopFacility> findTransitStop(String stopId) {
        if (mergeStops.equals(MergeGtfsStops.doNotMerge) || !mappedStops.containsKey(stopId))
            return Id.create(prefix + stopId, TransitStopFacility.class);

        return mappedStops.get(stopId);
    }


    private TransitRoute findOrAddTransitRoute(TransitLine tl, Route route, List<TransitRouteStop> stops) {
        for (TransitRoute tr : tl.getRoutes().values()) {
            if (tr.getStops().equals(stops)) {
                return tr;
            }
        }

        // no prefix needed because already included in transit line
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
        tr.getAttributes().putAttribute("simple_route_type", routeType.getSimpleTypeName());
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
        return Id.create(prefix + asciiShortName + "---" + route.route_id, TransitLine.class);
    }

    public enum MergeGtfsStops {doNotMerge, mergeStopsAtSameCoord, mergeToGtfsParentStation, mergeToParentAndRouteTypes}

    public static final class Builder {

        private GTFSFeed feed;
        private CoordinateTransformation transform;
        private LocalDate date = LocalDate.now();
        private boolean useExtendedRouteTypes = false;
        private MergeGtfsStops mergeStops = MergeGtfsStops.doNotMerge;
        private boolean includeMinimalTransferTimes = false;
        private Scenario scenario;
        private Predicate<Trip> includeTrip = (t) -> true;
        private Consumer<Stop> transformStop = (t) -> {
        };
        private Consumer<Route> transformRoute = (t) -> {
        };
        private Predicate<Stop> includeStop = (t) -> true;
        private Predicate<String> includeAgency = (t) -> true;
        private Predicate<Integer> includeRouteType = (t) -> true;
        private LocalDate startDate;
        private LocalDate endDate;
        private String prefix = "";

        private Builder() {
        }

        /**
         * Creates the converter instance.
         */
        public GtfsConverter build() {
            return new GtfsConverter(this);
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
         * Function to transform {@link Stop}.
         */
        public Builder setTransformStop(Consumer<Stop> transformStop) {
            this.transformStop = transformStop;
            return this;
        }

        public Builder setTransformRoute(Consumer<Route> transformRoute) {
            this.transformRoute = transformRoute;
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
        public Builder setMergeStops(MergeGtfsStops mergeStops) {
            this.mergeStops = mergeStops;
            return this;
        }

        /**
         * Id prefix to make prevent collisions
         */
        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        /**
         * Merge stops on the same coordinate.
         */
        public Builder setIncludeMinimalTransferTimes(boolean includeMinimalTransferTimes) {
            this.includeMinimalTransferTimes = includeMinimalTransferTimes;
            return this;
        }
    }

}
