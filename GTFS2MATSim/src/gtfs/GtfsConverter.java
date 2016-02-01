package gtfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;

import org.junit.Assert;
import org.mapdb.Fun.Tuple2;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.utils.geometry.CoordUtils;
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
import com.conveyal.gtfs.model.CalendarDate;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.Route;
import com.conveyal.gtfs.model.Service;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;

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

		// Get the used service Id for the choosen weekday and date
		List<String> usedServiceIds = new ArrayList<String>();
		usedServiceIds.addAll(this.getUsedServiceIds(this.feed.services));
		
		for(String serviceId: this.getUsedServiceIdsForSpecialDates(this.feed.services)){
			if(serviceId.charAt(0) == '+'){
				usedServiceIds.add(serviceId.substring(1));
			}else{
				if(usedServiceIds.contains(serviceId.substring(1))){
					usedServiceIds.remove(serviceId.substring(1));
				}
			}
		}
				
		System.out.println("Reading of ServiceIds succesfull: " + usedServiceIds);

		// Get the TripIds, which are available for the serviceIds
		List<Id<Trip>> usedTripIds = this.getUsedTripIds(this.feed.trips, usedServiceIds);
		System.out.println("Reading of TripIds succesfull: " + usedTripIds);


//		 Convert the schedules for the trips
		System.out.println("Convert the schedules");
		this.convertSchedules(this.feed.stop_times, tripToLine/*, tripRoute*/);

		// If you use the optional frequencies.txt, it will be transformed here
		this.convertFrequencies(this.feed.frequencies, tripToLine, usedTripIds);

		this.createTransitVehicles();

		if(usedTripIds.isEmpty()){
			System.out.println("There are no converted trips. You might need to change the date for better results.");
		}
		System.out.println("Conversion successfull");
	}
	
	
	/**
	 * Problem: Will also generate stops, and links for stops, which are disconnected from the network because
	 * they are not used by any line. These will have an invalid link reference. :(
	 * @param stops 
	 */
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
	
	
	private List<String> getUsedServiceIds(Map<String, Service> services) {
		List<String> serviceIds = new ArrayList<String>();
		System.out.println("Used Date for active schedules: " + this.date.toString() + " (weekday: " + date.getDayOfWeek().toString() + "). If you want to choose another date, please specify it, before running the converter");
		for(Service service: services.values()){
			if(this.date != null){
        			if(service.activeOn(date)){
        				serviceIds.add(service.service_id);
        			}
			}
		}
		return serviceIds;
	}
	
	
	private List<String> getUsedServiceIdsForSpecialDates(Map<String, Service> services){
	    	System.out.println("Used Date for active schedules: " + this.date + ". If you want to choose another date, please specify it, before running the converter");
		List<String> serviceIds = new ArrayList<String>();
		for (Service service: services.values()) {
		    	for(CalendarDate calendarDate: service.calendar_dates.values()) {
        			String serviceId = service.service_id;
        			LocalDate exceptionDate = calendarDate.date;
        			int exceptionType = calendarDate.exception_type;
        			if (this.date != null) {
                			if (exceptionDate.equals(this.date)) {
                				if (exceptionType == 1) {
                					serviceIds.add("+" + serviceId);
                				} else {
                					serviceIds.add("-" + serviceId);
                				}
                			}
        			}
		    	}
		}
		return serviceIds;
	}
	
	
	private List<Id<Trip>> getUsedTripIds(Map<String, com.conveyal.gtfs.model.Trip> trips, List<String> usedServiceIds) {
		List<Id<Trip>> usedTripIds = new ArrayList<>();
		for (com.conveyal.gtfs.model.Trip trip: trips.values()) {
			if (usedServiceIds.contains(trip.service.service_id)) {
				usedTripIds.add(Id.create(trip.trip_id, Trip.class));
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
	
	
	private void convertSchedules(ConcurrentNavigableMap<Tuple2, StopTime> stop_times, Map<Id<Trip>, Id<TransitLine>> tripToLineAssignments){
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
			System.out.println(nRow++ + "/" + nRows);
			Id<Trip> currentTripId = Id.create(currentTrip, Trip.class);
			Id<Trip> tripId = Id.create(stopTime.trip_id, Trip.class);
			Id<TransitStopFacility> stopId = Id.create(stopTime.stop_id, TransitStopFacility.class);
			if(stopTime.trip_id.equals(currentTrip)){
				TransitStopFacility stop = ts.getFacilities().get(stopId);
				TransitRouteStop routeStop = ts.getFactory().createTransitRouteStop(stop, Time.parseTime(String.valueOf(stopTime.arrival_time))-departureTime, Time.parseTime(String.valueOf(stopTime.departure_time))-departureTime);
				stops.add(routeStop);	
			}else{
				//finish old route
				stops = this.interpolateMissingDepartures(stops);
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
		stops = this.interpolateMissingDepartures(stops);
		TransitLine tl = ts.getTransitLines().get(tripToLineAssignments.get(currentTripId));
		Id<TransitRoute> routeId = this.matsimRouteIdToGtfsTripIdAssignments.get(currentTripId);
		TransitRoute tr = findOrAddTransitRoute(tl, stops,  /*networkRoute,*/ routeId);
		tr.addDeparture(departure);
	}
	
	
	/**
	 * Beats me how this works. michaz '13
	 * @param stops
	 * @return
	 */
	private List<TransitRouteStop> interpolateMissingDepartures(List<TransitRouteStop> stops) {
		List<TransitRouteStop> result = new ArrayList<TransitRouteStop>();
		List<TransitRouteStop> toBeInterpolated = new ArrayList<TransitRouteStop>();
		Map<Integer,TransitRouteStop> toBeReplaced = new HashMap<Integer,TransitRouteStop>();
		boolean properDeparture = true;
		int lastProperDepartureIndex = 0;
		for(Iterator<TransitRouteStop> it = stops.iterator(); it.hasNext(); ){
			TransitRouteStop s = it.next();
			if(Double.isInfinite(s.getDepartureOffset())){
				toBeInterpolated.add(s);
				properDeparture = false;
			}else if(!properDeparture){
				double totalLength = 0;
				TransitRouteStop lastProperStop = stops.get(lastProperDepartureIndex);
				for(TransitRouteStop sr: toBeInterpolated){
					totalLength = CoordUtils.calcDistance(sr.getStopFacility().getCoord(), lastProperStop.getStopFacility().getCoord()) + totalLength;
				}
				totalLength = totalLength + CoordUtils.calcDistance(toBeInterpolated.get(toBeInterpolated.size()-1).getStopFacility().getCoord(), s.getStopFacility().getCoord());
				double timeAvaible = s.getArrivalOffset() - lastProperStop.getDepartureOffset();
				double oldDepartureOffset = lastProperStop.getDepartureOffset();
				for(Iterator<TransitRouteStop> it2 = toBeInterpolated.iterator(); it2.hasNext();){
					TransitRouteStop sr = it2.next();
					double newDepartureOffset = (CoordUtils.calcDistance(sr.getStopFacility().getCoord(), lastProperStop.getStopFacility().getCoord()))/(totalLength) * timeAvaible + oldDepartureOffset;
					oldDepartureOffset = newDepartureOffset;
					TransitRouteStop newStop = ts.getFactory().createTransitRouteStop(sr.getStopFacility(), newDepartureOffset, newDepartureOffset);
					toBeReplaced.put(stops.indexOf(sr), newStop);
				}
				toBeInterpolated = new ArrayList<TransitRouteStop>();
				lastProperDepartureIndex = stops.indexOf(s);
				properDeparture = true;
			}else{
				lastProperDepartureIndex = stops.indexOf(s);
			}
		}
		for(TransitRouteStop s: stops){
			if(toBeReplaced.containsKey(stops.indexOf(s))){
				s.setAwaitDepartureTime(false);
				result.add(toBeReplaced.get(stops.indexOf(s)));
			}else{
				s.setAwaitDepartureTime(true);
				result.add(s);
			}
		}
		return result;
	}
	

	private void convertFrequencies(Map<String, Frequency> frequencies, Map<Id<Trip>, Id<TransitLine>> tripToRoute, List<Id<Trip>> usedTripIds) {
		int departureCounter = 2;
		String oldTripId = "";
		for(Frequency frequency: frequencies.values()){
			Id<Trip> tripId = Id.create(frequency.trip.trip_id, Trip.class);
			double startTime = Time.parseTime(String.valueOf(frequency.start_time));
			double endTime = Time.parseTime(String.valueOf(frequency.end_time));
			double step = Double.parseDouble(String.valueOf(frequency.headway_secs));
			// ---
			final Id<TransitLine> key = tripToRoute.get(tripId);
			Assert.assertNotNull(key);
			Id<TransitRoute> key2 = this.matsimRouteIdToGtfsTripIdAssignments.get(tripId);
			if(consolidatedRoutes.containsKey(key2)) {
			    System.out.println("used consolidated route.");
			    key2 = consolidatedRoutes.get(key2);
			}
			Assert.assertNotNull(key2);
			final TransitLine transitLine = ts.getTransitLines().get(key);
			Assert.assertNotNull( transitLine );
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
				System.out.println("Consolidated route " + routeId + "into " + tr.getId());
				consolidatedRoutes.put(routeId, tr.getId());
				return tr;
			} 
		}
		TransitRoute tr = ts.getFactory().createTransitRoute(routeId, /*networkRoute*/ null, stops, "pt");
		tl.addRoute(tr);
		return tr;
	}
	
	
	private static class Trip {
		
	}
}
