package org.matsim.contrib.gtfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import com.conveyal.gtfs.model.*;
import org.mapdb.Fun.Tuple2;
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
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;

import com.conveyal.gtfs.GTFSFeed;

public class GtfsConverter {
    
    	private Map<Id<TransitRoute>, Id<TransitRoute>> consolidatedRoutes = new HashMap<>();
    	private GTFSFeed feed;
	private CoordinateTransformation transform;
	private MutableScenario scenario;
	private TransitSchedule ts;
	private Map<String,Integer> vehicleIdsAndTypes = new HashMap<String,Integer>();
	private Map<Id<TransitLine>,Integer> lineToVehicleType = new HashMap<>();
	private Map<Id<Trip>,Id<TransitRoute>> matsimRouteIdToGtfsTripIdAssignments = new HashMap<>();
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

		// Get the Routenames and the assigned Trips
		Map<Id<Trip>, Id<TransitLine>> gtfsRouteToMatsimLineID = getMatsimLineIds(this.feed.routes);

		Map<Id<Trip>,Id<TransitLine>> tripToLine = getTripToLineMap(this.feed.trips,gtfsRouteToMatsimLineID);

		// Create Transitlines
		this.createTransitLines(gtfsRouteToMatsimLineID);

		this.feed.services.values().stream().flatMap(service -> service.calendar_dates.keySet().stream()).min(LocalDate::compareTo).ifPresent(startDate -> {
			System.out.println("Earliest service date: "+startDate);
		});
		this.feed.services.values().stream().flatMap(service -> service.calendar_dates.keySet().stream()).max(LocalDate::compareTo).ifPresent(endDate -> {
			System.out.println("Latest service date: " + endDate);
		});

		// Get the used service Id for the chosen weekday and date
		List<String> activeServiceIds = this.getActiveServiceIds(this.feed.services);
		System.out.printf("Active Services: %d %s\n", activeServiceIds.size(), activeServiceIds);

		// Get the TripIds, which are available for the serviceIds
		List<Trip> activeTripIds = this.getActiveTripIds(this.feed.trips);
		System.out.printf("Active trips: %d %s\n", activeTripIds.size(), activeTripIds);


//		 Convert the schedules for the trips
		System.out.println("Convert the schedules");
		this.convertStopTimes(this.feed.stop_times, tripToLine/*, tripRoute*/);

		// If you use the optional frequencies.txt, it will be transformed here
		this.convertFrequencies(this.feed.frequencies, tripToLine, activeTripIds);

		this.createTransitVehicles();

		if(activeTripIds.isEmpty()){
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
	
	
	private Map<Id<Trip>, Id<TransitLine>> getMatsimLineIds(Map<String, Route> routes) {
		Map<Id<Trip>, Id<TransitLine>> routeNames = new HashMap<>();
		for(Route route: routes.values()){
			String shortName = route.route_short_name;
			String oldId = route.route_id;
			oldId = oldId.replace("_", "-");
			if(shortName != null) {
			    shortName = shortName.replace("_", "-");
			} else {
				System.out.println("Route " + oldId + " has no short route name");
				shortName = "NoShortRouteName";
			}
			Id<TransitLine> newId = Id.create(oldId + "_" + shortName, TransitLine.class);
			routeNames.put(Id.create(route.route_id, Trip.class),newId);
			this.lineToVehicleType.put(newId, route.route_type);
		}
		return routeNames;
	}
	
	
	private Map<Id<Trip>,Id<TransitLine>> getTripToLineMap(Map<String, com.conveyal.gtfs.model.Trip> trips, Map<Id<Trip>, Id<TransitLine>> gtfsRouteToMatsimLineID){
		Map<Id<Trip>,Id<TransitLine>> tripLineAssignment = new HashMap<>();
		for(com.conveyal.gtfs.model.Trip trip: trips.values()) {
			tripLineAssignment.put(Id.create(trip.trip_id, Trip.class), gtfsRouteToMatsimLineID.get(Id.create(trip.route.route_id, Trip.class)));				
		}
		return tripLineAssignment;		
	}

	
	private void createTransitLines(Map<Id<Trip>, Id<TransitLine>> gtfsToMatsimRouteId) {
		for(Id<Trip> id: gtfsToMatsimRouteId.keySet()){
			TransitLine tl = ts.getFactory().createTransitLine(gtfsToMatsimRouteId.get(id));
			ts.addTransitLine(tl);
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


	private List<com.conveyal.gtfs.model.Trip> getActiveTripIds(Map<String, com.conveyal.gtfs.model.Trip> trips) {
		List<com.conveyal.gtfs.model.Trip> usedTripIds = new ArrayList<>();
		for (com.conveyal.gtfs.model.Trip trip: trips.values()) {
			if (trip.service.activeOn(this.date)) {
				usedTripIds.add(trip);
			}
			if(trip.trip_headsign!=null){
				String headsign = trip.trip_headsign;
				String oldId = trip.trip_id;
				oldId = oldId.replace("_", "-");
				headsign = headsign.replace("_", "-");
				this.matsimRouteIdToGtfsTripIdAssignments.put(Id.create(trip.trip_id, Trip.class), Id.create(oldId + "_" + headsign, TransitRoute.class));
			}else{
				this.matsimRouteIdToGtfsTripIdAssignments.put(Id.create(trip.trip_id, Trip.class), Id.create(trip.trip_id, TransitRoute.class));
			}
		}
		return usedTripIds;
	}
	
	
	private void convertStopTimes(ConcurrentNavigableMap<Tuple2, StopTime> stop_times, Map<Id<Trip>, Id<TransitLine>> tripToLineAssignments){
		List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
		Iterator<StopTime> it = stop_times.values().iterator();
		StopTime stopTime = it.next();
		String currentTrip = stopTime.trip_id;
		Double departureTime = Time.parseTime(String.valueOf(stopTime.departure_time));
		Departure departure = ts.getFactory().createDeparture(Id.create(currentTrip, Departure.class), departureTime);
		String vehicleId = stopTime.trip_id;
		departure.setVehicleId(Id.create(vehicleId, Vehicle.class));		
		this.vehicleIdsAndTypes.put(vehicleId,lineToVehicleType.get(tripToLineAssignments.get(Id.create(stopTime.trip_id, Trip.class))));
		int nRows = stop_times.values().size();
		int nRow = 1;
		for(;it.hasNext();) {
		    	stopTime = it.next();
//			System.out.println(nRow++ + "/" + nRows);
			Id<Trip> currentTripId = Id.create(currentTrip, Trip.class);
			Id<Trip> tripId = Id.create(stopTime.trip_id, Trip.class);
			Id<TransitStopFacility> stopId = Id.create(stopTime.stop_id, TransitStopFacility.class);
			if(stopTime.trip_id.equals(currentTrip)){
				TransitStopFacility stop = ts.getFacilities().get(stopId);
				TransitRouteStop routeStop = ts.getFactory().createTransitRouteStop(stop, Time.parseTime(String.valueOf(stopTime.arrival_time))-departureTime, Time.parseTime(String.valueOf(stopTime.departure_time))-departureTime);
				stops.add(routeStop);	
			}else{
				//finish old route
				TransitLine tl = ts.getTransitLines().get(tripToLineAssignments.get(currentTripId));
				Id<TransitRoute> routeId = this.matsimRouteIdToGtfsTripIdAssignments.get(currentTripId);
				TransitRoute tr = findOrAddTransitRoute(tl, stops,  /*networkRoute,*/ routeId);
				tr.addDeparture(departure);
				stops = new LinkedList<TransitRouteStop>();
				//begin new route
				departureTime = Time.parseTime(String.valueOf(stopTime.departure_time));
				departure = ts.getFactory().createDeparture(Id.create(stopTime.trip_id, Departure.class), departureTime);
				vehicleId = tripId.toString();
				departure.setVehicleId(Id.create(vehicleId, Vehicle.class));
				this.vehicleIdsAndTypes.put(vehicleId,lineToVehicleType.get(tripToLineAssignments.get(tripId)));												
				TransitStopFacility stop = ts.getFacilities().get(stopId);
				TransitRouteStop routeStop = ts.getFactory().createTransitRouteStop(stop, 0, Time.parseTime(String.valueOf(stopTime.departure_time))-departureTime);
				stops.add(routeStop);
				currentTrip = stopTime.trip_id;						
			}						
		}
		// The last trip of the file was not added, so it needs to be added now
		Id<Trip> currentTripId = Id.create(currentTrip, Trip.class);
		//finish old route
		TransitLine tl = ts.getTransitLines().get(tripToLineAssignments.get(currentTripId));
		Id<TransitRoute> routeId = this.matsimRouteIdToGtfsTripIdAssignments.get(currentTripId);
		TransitRoute tr = findOrAddTransitRoute(tl, stops,  /*networkRoute,*/ routeId);
		tr.addDeparture(departure);
	}


	private void convertFrequencies(Map<String, Frequency> frequencies, Map<Id<Trip>, Id<TransitLine>> tripToRoute, List<Trip> usedTripIds) {
		int departureCounter = 2;
		String oldTripId = "";
		for(Frequency frequency: frequencies.values()){
			Id<Trip> tripId = Id.create(frequency.trip.trip_id, Trip.class);
			double startTime = Time.parseTime(String.valueOf(frequency.start_time));
			double endTime = Time.parseTime(String.valueOf(frequency.end_time));
			double step = Double.parseDouble(String.valueOf(frequency.headway_secs));
			// ---
			final Id<TransitLine> key = tripToRoute.get(tripId);
			if (key == null) {
				throw new RuntimeException();
			}
			Id<TransitRoute> key2 = this.matsimRouteIdToGtfsTripIdAssignments.get(tripId);
			if(consolidatedRoutes.containsKey(key2)) {
			    System.out.println("used consolidated route.");
			    key2 = consolidatedRoutes.get(key2);
			}
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
			if((!(frequency.trip.trip_id.equals(oldTripId))) && (usedTripIds.contains(tripId))){
				departureCounter = transitRoute.getDepartures().size();
			}
			if(usedTripIds.contains(tripId)){
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
						Departure d = ts.getFactory().createDeparture(Id.create(tripId.toString() + "." + departureCounter, Departure.class), time);
						d.setVehicleId(Id.create(tripId.toString() + "." + departureCounter, Vehicle.class));
						this.vehicleIdsAndTypes.put(tripId.toString() + "." + departureCounter,this.lineToVehicleType.get(key));
						transitRoute.addDeparture(d);
						departureCounter++;
					}						
					time = time + step;
				}while(time <= endTime);		
			}
			oldTripId = frequency.trip.trip_id;
		}
	}

	
	private void createTransitVehicles(){
		for(String s: vehicleIdsAndTypes.keySet()){
			// TYPE
			VehicleType vt;
			int gtfsVehicleType = vehicleIdsAndTypes.get(s);
			vt = getVehicleType(gtfsVehicleType);

			// Vehicle
			Vehicle v = scenario.getTransitVehicles().getFactory().createVehicle(Id.create(s, Vehicle.class), vt);
			scenario.getTransitVehicles().addVehicle(v);
		}
	}

	
	private VehicleType getVehicleType(int gtfsVehicleType) {
	    	VehicleType vt;
	    	switch(gtfsVehicleType) {
        	    	// These are route types as specified by gtfs
        	    	case 0: vt = vehicleType(Id.create("type-tram", VehicleType.class)); break;
        	    	case 1: vt = vehicleType(Id.create("type-subway", VehicleType.class)); break;
        	    	case 2: vt = vehicleType(Id.create("type-rail", VehicleType.class)); break;
        	    	case 3: vt = vehicleType(Id.create("type-bus", VehicleType.class)); break;
        	    	case 4: vt = vehicleType(Id.create("type-ferry", VehicleType.class)); break;
        	    	case 5: vt = vehicleType(Id.create("type-cablecar", VehicleType.class)); break;
        	    	case 6: vt = vehicleType(Id.create("type-gondola", VehicleType.class)); break;
        	    	case 7: vt = vehicleType(Id.create("type-funicular", VehicleType.class)); break;
        	    	default: vt = vehicleType(Id.create("type-unidentified", VehicleType.class));
	    	}
	    	return vt;
	}
	

	private VehicleType vehicleType(Id<VehicleType> type) {
	    	VehicleType vt = scenario.getTransitVehicles().getVehicleTypes().get(type);
	    	if (vt == null) {
	    	    	VehicleCapacity vc = scenario.getTransitVehicles().getFactory().createVehicleCapacity();
	    	    	vc.setSeats(50);
	    	    	vc.setStandingRoom(50);
	    	    	vt = scenario.getTransitVehicles().getFactory().createVehicleType(type);
	    	    	vt.setCapacity(vc);
	    	    	vt.setLength(5);
	    	    	scenario.getTransitVehicles().addVehicleType(vt);
	    	}
	    	return vt;
	}
	

	private TransitRoute findOrAddTransitRoute(TransitLine tl, List<TransitRouteStop> stops,  Id<TransitRoute> routeId) {
		for (TransitRoute tr : tl.getRoutes().values()) {
			if (tr.getStops().equals(stops)) {
//				System.out.println("Consolidated route " + routeId + "into " + tr.getId());
				consolidatedRoutes.put(routeId, tr.getId());
				return tr;
			} 
		}
		TransitRoute tr = ts.getFactory().createTransitRoute(routeId, /*networkRoute*/ null, stops, "pt");
		tl.addRoute(tr);
		return tr;
	}
	
}
