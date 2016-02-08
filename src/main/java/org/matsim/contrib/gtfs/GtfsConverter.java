package org.matsim.contrib.gtfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
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

	public GtfsConverter(GTFSFeed feed, Scenario scenario, CoordinateTransformation transform) {
		this.feed = feed;
		this.transform = transform;
		this.scenario = (MutableScenario) scenario;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}
	public void convert(){

		this.ts = scenario.getTransitSchedule();

		// Put all stops in the Schedule
		this.convertStops(this.feed.stops);

		this.feed.services.values().stream().flatMap(service -> service.calendar_dates.keySet().stream()).min(LocalDate::compareTo).ifPresent(startDate -> {
			System.out.println("Earliest service date: "+startDate);
		});
		this.feed.services.values().stream().flatMap(service -> service.calendar_dates.keySet().stream()).max(LocalDate::compareTo).ifPresent(endDate -> {
			System.out.println("Latest service date: " + endDate);
		});

		// Get the used service Id for the chosen weekday and date
		List<String> activeServiceIds = this.getActiveServiceIds(this.feed.services);
		System.out.printf("Active Services: %d %s\n", activeServiceIds.size(), activeServiceIds);

		// Get the Trips which are active today
		List<Trip> activeTrips = this.getActiveTrips();
		System.out.printf("Active Trips: %d %s\n", activeTrips.size(), activeTrips.stream().map(trip -> trip.trip_id).collect(Collectors.toList()));

		activeTrips.stream().map(trip -> trip.route).distinct().forEach(route -> {
			TransitLine tl = ts.getFactory().createTransitLine(Id.create(route.route_id, TransitLine.class));
			ts.addTransitLine(tl);
		});


		System.out.println("Convert the schedules");
		this.convertStopTimes(activeTrips);

		// If you use the optional frequencies.txt, it will be transformed here
		this.convertFrequencies(activeTrips);

//		this.createTransitVehicles();

		if(activeTrips.isEmpty()){
			System.out.println("There are no converted trips. You might need to change the date for better results.");
		}
		System.out.println("Conversion successfull");
	}
	
	
	private void convertStops(Map<String, Stop> stops ){
		for(Stop stop: stops.values()){
			TransitStopFacility t = this.ts.getFactory().createTransitStopFacility(Id.create(stop.stop_id, TransitStopFacility.class), transform.transform(new Coord(stop.stop_lon, stop.stop_lat)), false);
			t.setName(stop.stop_name);
			ts.addStopFacility(t);
		}		
	}


	private List<String> getActiveServiceIds(Map<String, Service> services) {
		List<String> serviceIds = new ArrayList<String>();
		System.out.println("Used Date for active schedules: " + this.date.toString() + " (weekday: " + date.getDayOfWeek().toString() + "). If you want to choose another date, please specify it, before running the converter");
		for(Service service: services.values()){
			if(service.activeOn(date)){
				serviceIds.add(service.service_id);
			}
		}
		return serviceIds;
	}


	private List<Trip> getActiveTrips() {
		List<Trip> usedTrips = new ArrayList<>();
		for (Trip trip: feed.trips.values()) {
			if (trip.service.activeOn(this.date)) {
				usedTrips.add(trip);
			}
		}
		return usedTrips;
	}
	
	
	private void convertStopTimes(List<Trip> trips) {
		for (Trip trip : trips) {
			List<TransitRouteStop> stops = new LinkedList<>();
			StopTime firstStopTime = feed.getOrderedStopTimesForTrip(trip.trip_id).iterator().next();
			Double departureTime = Time.parseTime(String.valueOf(firstStopTime.departure_time));
			Departure departure = ts.getFactory().createDeparture(Id.create(firstStopTime.trip_id, Departure.class), departureTime);
			for(StopTime stopTime : feed.getOrderedStopTimesForTrip(trip.trip_id)) {
				Id<TransitStopFacility> stopId = Id.create(stopTime.stop_id, TransitStopFacility.class);
				TransitStopFacility stop = ts.getFacilities().get(stopId);
				TransitRouteStop routeStop = ts.getFactory().createTransitRouteStop(stop, Time.parseTime(String.valueOf(stopTime.arrival_time))-departureTime, Time.parseTime(String.valueOf(stopTime.departure_time))-departureTime);
				stops.add(routeStop);
			}
			TransitLine tl = ts.getTransitLines().get(Id.create(trip.route.route_id, TransitLine.class));
			TransitRoute tr = findOrAddTransitRoute(tl, stops);
			tr.addDeparture(departure);
		}
	}


	private void convertFrequencies(List<Trip> trips) {
		int departureCounter = 2;
		String oldTripId = "";
		for (Trip trip : trips) {
			if (trip.frequencies != null) {
				for (Frequency frequency : trip.frequencies) {
					double startTime = Time.parseTime(String.valueOf(frequency.start_time));
					double endTime = Time.parseTime(String.valueOf(frequency.end_time));
					double step = Double.parseDouble(String.valueOf(frequency.headway_secs));
					// ---
					final Id<TransitLine> key = Id.create(trip.route.route_id, TransitLine.class);
					if (key == null) {
						throw new RuntimeException();
					}
					Id<TransitRoute> key2 = null;
					if (key2 == null) {
						throw new RuntimeException();
					}
					final TransitLine transitLine = ts.getTransitLines().get(key);
					if (transitLine == null) {
						throw new RuntimeException();
					}
					final TransitRoute transitRoute = transitLine.getRoutes().get(key2);
					if ( transitRoute==null ) {
						for ( Id<TransitRoute> key3 : transitLine.getRoutes().keySet() ) {
							System.err.println(  key3 ) ;
						}
						System.err.println( "key=" + key ) ;
						System.err.println( "key2=" + key2 ) ;
						System.err.println( "transitLine=" + transitLine ) ;
						System.err.println("does not exist; skipping ...") ;
						continue ;
					}
					if((!(frequency.trip.trip_id.equals(oldTripId))) && (trips.contains(frequency.trip.trip_id))){
						departureCounter = transitRoute.getDepartures().size();
					}
					if(trips.contains(frequency.trip.trip_id)){
						Map<Id<Departure>, Departure> depatures = transitRoute.getDepartures();
						double latestDeparture = 0;
						for(Departure d: depatures.values()){
							if(latestDeparture < d.getDepartureTime()){
								latestDeparture = d.getDepartureTime();
							}
						}
						double time = latestDeparture + step;
						do{
							if(time>startTime){
								Departure d = ts.getFactory().createDeparture(Id.create(frequency.trip.trip_id.toString() + "." + departureCounter, Departure.class), time);
								transitRoute.addDeparture(d);
								departureCounter++;
							}
							time = time + step;
						}while(time <= endTime);
					}
					oldTripId = frequency.trip.trip_id;
				}
			}
		}
	}

	private TransitRoute findOrAddTransitRoute(TransitLine tl, List<TransitRouteStop> stops) {
		for (TransitRoute tr : tl.getRoutes().values()) {
			if (tr.getStops().equals(stops)) {
				return tr;
			} 
		}
		Id<TransitRoute> routeId = Id.create(tl.getId().toString() + "_" + tl.getRoutes().size(), TransitRoute.class);
		TransitRoute tr = ts.getFactory().createTransitRoute(routeId, /*networkRoute*/ null, stops, "pt");
		tl.addRoute(tr);
		return tr;
	}
	
}
