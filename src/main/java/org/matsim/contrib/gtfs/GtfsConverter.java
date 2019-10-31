package org.matsim.contrib.gtfs;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.conveyal.gtfs.model.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.conveyal.gtfs.GTFSFeed;

public class GtfsConverter {
    
	private GTFSFeed feed;
	private CoordinateTransformation transform;
	private MutableScenario scenario;
	private TransitSchedule ts;
	private LocalDate date = LocalDate.now();
	private boolean useExtendedRouteTypes;

	public GtfsConverter(GTFSFeed feed, Scenario scenario, CoordinateTransformation transform, boolean useExtendedRouteTypes) {
		this.feed = feed;
		this.transform = transform;
		this.scenario = (MutableScenario) scenario;
		this.useExtendedRouteTypes = useExtendedRouteTypes;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public void convert(){

		this.ts = scenario.getTransitSchedule();

		// Put all stops in the Schedule
		this.convertStops();

		LocalDate startDate = LocalDate.MAX;
		for(Service service: this.feed.services.values()) {
		    if(service.calendar !=null && service.calendar.start_date.isBefore(startDate)) {
				startDate = service.calendar.start_date;
		    }
			if(service.calendar_dates != null) {
				for (LocalDate exceptionDate : service.calendar_dates.keySet()) {
					if (exceptionDate.isBefore(startDate)) {
						startDate = exceptionDate;
					}
				}
			}
		}
		System.out.println("Earliest date mentioned in feed: "+startDate);

		LocalDate endDate = LocalDate.MIN;
		for(Service service: this.feed.services.values()) {
		    if(service.calendar !=null && service.calendar.end_date.isAfter(endDate)) {
				endDate = service.calendar.end_date;
		    }
			if(service.calendar_dates != null) {
				for (LocalDate exceptionDate : service.calendar_dates.keySet()) {
					if (exceptionDate.isAfter(endDate)) {
						endDate = exceptionDate;
					}
				}
			}

		}
		System.out.println("Latest date mentioned in feed: " + endDate);

		// Get the used service Id for the chosen weekday and date
		List<String> activeServiceIds = this.getActiveServiceIds(this.feed.services);
		System.out.printf("Active Services: %d %s\n", activeServiceIds.size(), activeServiceIds);

		// Get the Trips which are active today
		List<Trip> activeTrips = feed.trips.values().stream().filter(trip -> feed.services.get(trip.service_id).activeOn(this.date)).collect(Collectors.toList());
		System.out.printf("Active Trips: %d %s\n", activeTrips.size(), activeTrips.stream().map(trip -> trip.trip_id).collect(Collectors.toList()));

		// Create one TransitLine for each GTFS-Route which has an active trip
		activeTrips.stream().map(trip -> feed.routes.get(trip.route_id)).distinct().forEach(route -> {
			TransitLine tl = ts.getFactory().createTransitLine(getReadableTransitLineId(route));
			ts.addTransitLine(tl);
			tl.getAttributes().putAttribute("gtfs_agency_id", String.valueOf(route.agency_id));
			tl.getAttributes().putAttribute("gtfs_route_type", String.valueOf(route.route_type));
			
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

		if(activeTrips.isEmpty()){
			System.out.println("There are no converted trips. You might need to change the date for better results.");
		}
		System.out.println("Conversion successfull");
	}
	
	
	private void convertStops(){
		for(Stop stop: feed.stops.values()){
			TransitStopFacility t = this.ts.getFactory().createTransitStopFacility(Id.create(stop.stop_id, TransitStopFacility.class), transform.transform(new Coord(stop.stop_lon, stop.stop_lat)), false);
			t.setName(stop.stop_name);
			ts.addStopFacility(t);
		}		
	}


	private List<String> getActiveServiceIds(Map<String, Service> services) {
		List<String> serviceIds = new ArrayList<>();
		System.out.println("Used Date for active schedules: " + this.date.toString() + " (weekday: " + date.getDayOfWeek().toString() + "). If you want to choose another date, please specify it, before running the converter");
		for(Service service: services.values()){
			if(service.activeOn(date)){
				serviceIds.add(service.service_id);
			}
		}
		return serviceIds;
	}
	
	
	private int asGtfsDate(LocalDate date) {
		return date.getYear() * 10000 + this.date.getMonthValue() * 100 + this.date.getDayOfMonth();
	}


	private void convertTrips(List<Trip> trips) {
		int scheduleDepartures = 0;
		int frequencyDepartures = 0;
		for (Trip trip : trips) {
			if (feed.getFrequencies(trip.trip_id).isEmpty() && feed.getOrderedStopTimesForTrip(trip.trip_id).iterator().hasNext()) {		
				StopTime firstStopTime = feed.getOrderedStopTimesForTrip(trip.trip_id).iterator().next();
				Double departureTime = Time.parseTime(String.valueOf(firstStopTime.departure_time));
				List<TransitRouteStop> stops = new ArrayList<>();
				try {
					for(StopTime stopTime : feed.getInterpolatedStopTimesForTrip(trip.trip_id)) {
						Id<TransitStopFacility> stopId = Id.create(stopTime.stop_id, TransitStopFacility.class);
						TransitStopFacility stop = ts.getFacilities().get(stopId);
						double arrivalOffset;
						if (stopTime.arrival_time != Integer.MIN_VALUE) {
							arrivalOffset = Time.parseTime(String.valueOf(stopTime.arrival_time)) - departureTime;
						} else {
							arrivalOffset = Time.UNDEFINED_TIME;
						}
						double departureOffset;
						if (stopTime.departure_time != Integer.MIN_VALUE) {
							departureOffset = Time.parseTime(String.valueOf(stopTime.departure_time)) - departureTime;
						} else {
							departureOffset = Time.UNDEFINED_TIME;
						}
						TransitRouteStop routeStop = ts.getFactory().createTransitRouteStop(stop, arrivalOffset, departureOffset);
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
				for(StopTime stopTime : feed.getOrderedStopTimesForTrip(trip.trip_id)) {
					Id<TransitStopFacility> stopId = Id.create(stopTime.stop_id, TransitStopFacility.class);
					TransitStopFacility stop = ts.getFacilities().get(stopId);
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
		System.out.println("Created schedule-based departures: " + scheduleDepartures);
		System.out.println("Created frequency-based departures: " + frequencyDepartures);
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
	
}
