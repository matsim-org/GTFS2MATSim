package gtfs;


import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.CalendarDate;
import com.conveyal.gtfs.model.Route;
import com.conveyal.gtfs.model.Service;
import com.conveyal.gtfs.model.Stop;
import com.conveyal.gtfs.model.StopTime;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentNavigableMap;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
//
//import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.mapdb.Fun.Tuple2;
import java.util.Map;
import java.util.Set;
//
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
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
//
//import com.vividsolutions.jts.util.Assert;
//
//
public class GtfsConverter {
    
    	private GTFSFeed feed;
	private CoordinateTransformation transform;
	private MutableScenario scenario;
	private TransitSchedule ts;
	private Map<String,Integer> vehicleIdsAndTypes = new HashMap<String,Integer>();
	private Map<Id<TransitLine>,Integer> lineToVehicleType = new HashMap<>();
	private Map<Id<Trip>,Id<TransitRoute>> matsimRouteIdToGtfsTripIdAssignments = new HashMap<>();
	private boolean createShapedNetwork = false;
//
	private LocalDate date = LocalDate.now();
//
//
	// Fields for shaped Network
	// (TripId,ShapeId)
	private Map<String,String> shapeIdToTripIdAssignments = new HashMap<String,String>();
	// (LinkId,(TripId,FromShapeDist,ToShapeDist))
	private Map<Id<Link>,String[]> shapedLinkIds = new HashMap<>();
	// If there is no shape_dist_traveled field, try to identify the stations by its coordinates
	// (LinkId,(TripId,FromCoordX, FromCoordY ,ToCoordX, ToCoordY)) - Both Coordinates as Strings and in Matsim-KS
	private Map<Id<Link>,String[]> shapedLinkIdsCoordinate = new HashMap<>();
	private boolean alternativeStationToShapeAssignment = false;
	private double toleranceInM = 0;
//	// (ShapeId, (shapeDist, x, y))
	private Map<Id<Shape>,List<String[]>> shapes = new HashMap<Id<Shape>,List<String[]>>();
//
//
//
//	
	public GtfsConverter(GTFSFeed feed, Scenario scenario, CoordinateTransformation transform) {
		this.feed = feed;
		this.transform = transform;
		this.scenario = (MutableScenario) scenario;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}
//
//	public void setCreateShapedNetwork(boolean createShapedNetwork) {
//		if(((new File(filepath + "/shapes.txt")).exists()) && (createShapedNetwork)){
//			this.createShapedNetwork = createShapedNetwork;
//		}else if(createShapedNetwork){
//			System.out.println("Couldn't find the 'shapes.txt'. No shaped network will be created!");
//		}else if(!createShapedNetwork){
//			this.createShapedNetwork = createShapedNetwork;
//		}
//	}
//
	public void convert(){
//		// Parse required Files
//		System.out.println("Parse required Files...");
//		GtfsSource stopsSource = GtfsSource.parseGtfsFile(filepath + "/stops.txt");
//		GtfsSource routesSource = GtfsSource.parseGtfsFile(filepath + "/routes.txt");
//		GtfsSource tripSource = GtfsSource.parseGtfsFile(filepath + "/trips.txt");
//		GtfsSource stopTimesSource = GtfsSource.parseGtfsFile(filepath + "/stop_times.txt");
//
//		// Parse optional Files
//		System.out.println("Parse optional Files...");
//		String calendarFilename = filepath + "/calendar.txt";
//		GtfsSource calendarSource = null;
//		if((new File(calendarFilename)).exists()){
//			calendarSource = GtfsSource.parseGtfsFile(calendarFilename);
//		}
//		String calendarDatesFilename = filepath + "/calendar_dates.txt";
//		GtfsSource calendarDatesSource = null;
//		if((new File(calendarDatesFilename)).exists()){
//			calendarDatesSource = GtfsSource.parseGtfsFile(calendarDatesFilename);
//		}
//		String frequenciesFilename = filepath + "/frequencies.txt";
//		GtfsSource frequenciesSource = null;
//		if((new File(frequenciesFilename)).exists()){
//			frequenciesSource = GtfsSource.parseGtfsFile(frequenciesFilename);
//		}
//		String shapesFilename = filepath + "/shapes.txt";;
//		GtfsSource shapesSource = null;
//		if(this.createShapedNetwork){
//			if((new File(shapesFilename)).exists()){
//				shapesSource = GtfsSource.parseGtfsFile(shapesFilename);
//			}else{
//				System.out.println(shapesFilename + " doesn't exist - no shaped network will be created");
//				this.createShapedNetwork = false;
//			}
//		}
//
		this.ts = scenario.getTransitSchedule();
//
//		// Put all stops in the Schedule
		this.convertStops(this.feed.stops);
//
//		// Get the Routenames and the assigned Trips
		Map<Id<Trip>, Id<TransitLine>> gtfsRouteToMatsimLineID = getMatsimLineIds(this.feed.routes);
//
		Map<Id<Trip>,Id<TransitLine>> tripToRoute = getTripToRouteMap(this.feed.trips,gtfsRouteToMatsimLineID);
//
//		// Create Transitlines
		this.createTransitLines(gtfsRouteToMatsimLineID);
//
//
//
//		// Get the used service Id for the choosen weekday and date
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

		// Create the Network
//		System.out.println("Creating Network");
//		this.createNetworkOfStopsAndTrips(this.feed.stop_times, ts);

		// Get the TripRoutes
//		Map<Id<Trip>,NetworkRoute> tripRoute;
//		System.out.println("Create NetworkRoutes");
//		tripRoute = createNetworkRoutes(this.feed.stop_times);
//		if(this.createShapedNetwork){
//			System.out.println("Creating shaped Network");
//			this.convertShapes(this.feed.shapes);
//			Map<Id<Link>, List<Coord>> shapedLinks = this.assignShapesToLinks();
//			NetworkEnricher networkEnricher = new NetworkEnricher(scenario.getNetwork());
//			tripRoute = networkEnricher.replaceLinks(shapedLinks, tripRoute);
//			scenario.setNetwork(networkEnricher.getEnrichedNetwork());
//		}

//		 Convert the schedules for the trips
		System.out.println("Convert the schedules");
		this.convertSchedules(this.feed.stop_times, tripToRoute/*, tripRoute*/);

//		// If you use the optional frequencies.txt, it will be transformed here
//		if((new File(frequenciesFilename)).exists()){
//			this.convertFrequencies(frequenciesSource, tripToRoute, usedTripIds);
//		}
//
//		this.createTransitVehicles();
//
//		if(usedTripIds.isEmpty()){
//			System.out.println("There are no converted trips. You might need to change the date for better results.");
//		}
//		System.out.println("Conversion successfull");
	}

	private List<Id<Trip>> getUsedTripIds(Map<String, com.conveyal.gtfs.model.Trip> trips, List<String> usedServiceIds) {
		List<Id<Trip>> usedTripIds = new ArrayList<>();
		for (com.conveyal.gtfs.model.Trip trip: trips.values()) {
			if (usedServiceIds.contains(trip.service.service_id)) {
				usedTripIds.add(Id.create(trip.trip_id, Trip.class));
			}
			if (trip.shape_id != null) {
				String shapeId = trip.shape_id;
				this.shapeIdToTripIdAssignments.put(trip.trip_id, shapeId);
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
//
	private Map<Id<Trip>,Id<TransitLine>> getTripToRouteMap(Map<String, com.conveyal.gtfs.model.Trip> trips, Map<Id<Trip>, Id<TransitLine>> gtfsToMatsimRouteIdAssingments){
		Map<Id<Trip>,Id<TransitLine>> routeTripAssignment = new HashMap<>();
		for(com.conveyal.gtfs.model.Trip trip: trips.values()) {
			routeTripAssignment.put(Id.create(trip.trip_id, Trip.class), gtfsToMatsimRouteIdAssingments.get(Id.create(trip.route.route_id, Trip.class)));				
		}
		return routeTripAssignment;		
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
//
//	private void convertFrequencies(GtfsSource frequenciesSource, Map<Id<Trip>, Id<TransitLine>> routeToTripAssignments, List<Id<Trip>> usedTripIds) {
//		int tripIdIndex = frequenciesSource.getContentIndex("trip_id");
//		int startTimeIndex = frequenciesSource.getContentIndex("start_time");
//		int endTimeIndex = frequenciesSource.getContentIndex("end_time");
//		int stepIndex = frequenciesSource.getContentIndex("headway_secs");
//		int departureCounter = 2;
//		String oldTripId = "";
//		for(String[] entries: frequenciesSource.getContent()){
//			Id<Trip> tripId = Id.create(entries[tripIdIndex], Trip.class);
//			double startTime = Time.parseTime(entries[startTimeIndex].trim());
//			double endTime = Time.parseTime(entries[endTimeIndex].trim());
//			double step = Double.parseDouble(entries[stepIndex]);
//			// ---
//			final Id<TransitLine> key = routeToTripAssignments.get(tripId);
//			Assert.assertNotNull(key);
//			final Id<TransitRoute> key2 = this.matsimRouteIdToGtfsTripIdAssignments.get(tripId);
//			Assert.assertNotNull(key2);
//			final TransitLine transitLine = ts.getTransitLines().get(key);
//			Assert.assertNotNull( transitLine );
//			final TransitRoute transitRoute = transitLine.getRoutes().get(key2);
//			if ( transitRoute==null ) {
//				for ( Id<TransitRoute> key3 : transitLine.getRoutes().keySet() ) {
//					System.err.println(  key3 ) ;
//				}
//				System.err.println( "key=" + key ) ;
//				System.err.println( "key2=" + key2 ) ;
//				System.err.println( "transitLine=" + transitLine ) ;
//				System.err.println("does not exist; skipping ...") ;
//				continue ;
//			}
//			// ---
//			if((!(entries[tripIdIndex].equals(oldTripId))) && (usedTripIds.contains(tripId))){
//				departureCounter = transitRoute.getDepartures().size();
//			}
//			if(usedTripIds.contains(tripId)){
//				Map<Id<Departure>, Departure> depatures = transitRoute.getDepartures();
//				double latestDeparture = 0;
//				for(Departure d: depatures.values()){
//					if(latestDeparture < d.getDepartureTime()){
//						latestDeparture = d.getDepartureTime();
//					}
//				}
//				double time = latestDeparture + step;				
//				do{
//					if(time>startTime){
//						Departure d = ts.getFactory().createDeparture(Id.create(tripId.toString() + "." + departureCounter, Departure.class), time);
//						d.setVehicleId(Id.create(tripId.toString() + "." + departureCounter, Vehicle.class));
//						this.vehicleIdsAndTypes.put(tripId.toString() + "." + departureCounter,this.lineToVehicleType.get(key));
//						transitRoute.addDeparture(d);
//						departureCounter++;
//					}						
//					time = time + step;
//				}while(time <= endTime);		
//			}
//			oldTripId = entries[tripIdIndex];
//		}
//	}


	private void convertSchedules(ConcurrentNavigableMap<Tuple2, StopTime> stop_times, Map<Id<Trip>, Id<TransitLine>> routeToTripAssignments){
		List<TransitRouteStop> stops = new LinkedList<TransitRouteStop>();
//		int tripIdIndex = stopTimesSource.getContentIndex("trip_id");
//		int arrivalTimeIndex = stopTimesSource.getContentIndex("arrival_time");
//		int departureTimeIndex = stopTimesSource.getContentIndex("departure_time");
//		int stopIdIndex = stopTimesSource.getContentIndex("stop_id");
//		String[] firstEntry = stopTimesSource.getContent().get(0);
		Iterator<StopTime> it = stop_times.values().iterator();
		StopTime stopTime = it.next();
		String currentTrip = stopTime.trip_id;
		Double departureTime = Time.parseTime(String.valueOf(stopTime.departure_time));
		Departure departure = ts.getFactory().createDeparture(Id.create(currentTrip, Departure.class), departureTime);
		String vehicleId = stopTime.trip_id;
		departure.setVehicleId(Id.create(vehicleId, Vehicle.class));		
		this.vehicleIdsAndTypes.put(vehicleId,lineToVehicleType.get(routeToTripAssignments.get(Id.create(stopTime.trip_id, Trip.class))));
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
				TransitLine tl = ts.getTransitLines().get(routeToTripAssignments.get(currentTripId));
				Id<TransitRoute> routeId = this.matsimRouteIdToGtfsTripIdAssignments.get(currentTripId);
//				NetworkRoute networkRoute = tripRoute.get(currentTripId);
				TransitRoute tr = findOrAddTransitRoute(tl, stops,  /*networkRoute,*/ routeId);
				tr.addDeparture(departure);
				stops = new LinkedList<TransitRouteStop>();
				//begin new route
				departureTime = Time.parseTime(String.valueOf(stopTime.departure_time));
				departure = ts.getFactory().createDeparture(Id.create(stopTime.trip_id, Departure.class), departureTime);
				vehicleId = tripId.toString();
				departure.setVehicleId(Id.create(vehicleId, Vehicle.class));
				this.vehicleIdsAndTypes.put(vehicleId,lineToVehicleType.get(routeToTripAssignments.get(tripId)));												
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
		TransitLine tl = ts.getTransitLines().get(routeToTripAssignments.get(currentTripId));
		Id<TransitRoute> routeId = this.matsimRouteIdToGtfsTripIdAssignments.get(currentTripId);
//		NetworkRoute networkRoute = tripRoute.get(currentTripId);
		TransitRoute tr = findOrAddTransitRoute(tl, stops,  /*networkRoute,*/ routeId);
		tr.addDeparture(departure);
	}
//
	private TransitRoute findOrAddTransitRoute(TransitLine tl, List<TransitRouteStop> stops,  Id<TransitRoute> routeId) {
		for (TransitRoute tr : tl.getRoutes().values()) {
			if (tr.getStops().equals(stops)) {
				System.out.println("Saved a route.");
				return tr;
			} 
		}
		TransitRoute tr = ts.getFactory().createTransitRoute(routeId, /*networkRoute*/ null, stops, "pt");
		tl.addRoute(tr);
		return tr;
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

//	private Map<Id<Trip>,NetworkRoute> createNetworkRoutes(ConcurrentNavigableMap<Tuple2, StopTime> stop_times) {
//		Map<Id<Trip>,NetworkRoute> tripRoutes = new HashMap<>();
//		LinkedList<Id<Link>> route = new LinkedList<Id<Link>>();
//		
//		Iterator<StopTime> it = stop_times.values().iterator();
//		StopTime stopTime = it.next();
//		String currentTrip = stopTime.trip_id;
//		String startStation = stopTime.stop_id;
//		for(; it.hasNext();) {
//		    stopTime = it.next();
//			String nextStation = stopTime.stop_id;				
//			if(currentTrip.equals(stopTime.trip_id)){					
//				if(!(startStation.equals(nextStation))){
//					Id<Node> startStationNodeId = Id.create(startStation, Node.class);
//					Id<Node> nextStationNodeId = Id.create(nextStation, Node.class);
//					Id<Link> linkId = findLinkFromNodeToNode(startStationNodeId, nextStationNodeId);
//					route.add(linkId);
//					route.add(Id.create("dL2_" + nextStation, Link.class));
//					route.add(ts.getFacilities().get(Id.create(stopTime.stop_id, TransitStopFacility.class)).getLinkId());
//				}else{
//					route.add(ts.getFacilities().get(Id.create(stopTime.stop_id, TransitStopFacility.class)).getLinkId());
//				}
//				startStation = nextStation;
//			}else{
//				NetworkRoute netRoute = (NetworkRoute) (new LinkNetworkRouteFactory()).createRoute(route.getFirst(), route.getLast());
//				if(route.size() > 2){
//					netRoute.setLinkIds(route.getFirst(), route.subList(1, route.size()-1), route.getLast());
//				}
//				tripRoutes.put(Id.create(currentTrip, Trip.class), netRoute);
//				// Start new Route
//				currentTrip = stopTime.trip_id;
//				route = new LinkedList<Id<Link>>();
//				route.add(ts.getFacilities().get(Id.create(stopTime.stop_id, TransitStopFacility.class)).getLinkId());
//				startStation = stopTime.stop_id;
//			}
//		}		
//		NetworkRoute netRoute = (NetworkRoute) (new LinkNetworkRouteFactory()).createRoute(route.getFirst(), route.getLast());
//		if(route.size() > 2){
//			netRoute.setLinkIds(route.getFirst(), route.subList(1, route.size()-1), route.getLast());
//		}
//		tripRoutes.put(Id.create(currentTrip, Trip.class), netRoute);
//		return tripRoutes;		
//	}


//	private Id<Link> findLinkFromNodeToNode(Id<Node> fromNodeId, Id<Node> toNodeId) {
//		Id<Link> linkId = null;
//		for(Id<Link> fromId: scenario.getNetwork().getNodes().get(fromNodeId).getOutLinks().keySet()){
//			for(Id<Link> toId: scenario.getNetwork().getNodes().get(toNodeId).getInLinks().keySet()){
//				if(fromId.equals(toId)){
//					linkId = fromId;
//				}
//			}
//		}
//		return linkId;
//	}

	private void createTransitLines(Map<Id<Trip>, Id<TransitLine>> gtfsToMatsimRouteId) {
		for(Id<Trip> id: gtfsToMatsimRouteId.keySet()){
			TransitLine tl = ts.getFactory().createTransitLine(gtfsToMatsimRouteId.get(id));
			ts.addTransitLine(tl);
		}		
	}

//	private void createNetworkOfStopsAndTrips( ConcurrentNavigableMap<Tuple2, StopTime> stop_times, TransitSchedule ts){
//		double freespeedKmPerHour=50;
//		double capacity = 1500.0;
//		int numLanes = 1;
//		long i = 0;
//		// To prevent the creation of similar links in different directions there need to be a Map which contains all existing connections
//		Map<Id<Node>,List<Id<Node>>> fromNodes = new HashMap<>();
//		// Create a new Network
//		Network network = scenario.getNetwork();
//		// Add all stops as nodes
//		Map<Id<TransitStopFacility>, TransitStopFacility> stops = ts.getFacilities();
//		Map<Id<Node>, ? extends Node> nodes = network.getNodes();
//		for(Id<TransitStopFacility> id: stops.keySet()){
//			TransitStopFacility stop = stops.get(id);
//			NodeImpl n = new NodeImpl(Id.create(id, Node.class));
//			n.setCoord(stop.getCoord());
//			network.addNode(n);
//			createDummyNodeAndLinks(Id.create(id, Node.class), capacity, numLanes, network, nodes);
//			stop.setLinkId(Id.create("dL1_"+ stop.getId().toString(), Link.class));
//		}
//		// Get the Links from the trips in stopTimesSource
////		if((shapeDistIndex < 0) && (this.createShapedNetwork)){
////			System.out.println("Couldn't find the shape_dist_traveled field in stop_times.txt. Now it uses the alternative station to shape assingment.");
////			this.alternativeStationToShapeAssignment = true;
////		}
//		boolean first = true;
//		StopTime stopTime = null;
//		for(java.util.Iterator<StopTime> it = stop_times.values().iterator(); it.hasNext();) {
//		    	if(first) {
//		    	   stopTime = it.next();
//		    	   first = false;
//		    	}
//		    	
//			boolean addLink = false;	
//			Id<Node> fromNodeId = Id.create(stopTime.stop_id, Node.class);
//			double departureTime = Time.parseTime(String.valueOf(stopTime.departure_time));
//			String usedTripId = stopTime.trip_id;
//			String fromShapeDist = "";
//			Coord fromShapeCoord = null;				
//			// Prepare the replacing with shaped links
//			if(createShapedNetwork){
//				if(!this.alternativeStationToShapeAssignment){
//					fromShapeDist = String.valueOf(stopTime.shape_dist_traveled);
//					if(fromShapeDist.isEmpty()){
//						fromShapeDist = "0.0";
//					}
//				}else{
//					//WARNING: Couldn't find shape_dist_traveled header in stop_times.txt. The converter will try to identify the Stations by its coordinates.
//					this.alternativeStationToShapeAssignment = true;
//					fromShapeCoord = network.getNodes().get(fromNodeId).getCoord();
//				}
//			}
//			if(it.hasNext()){
//				stopTime = it.next();
//				Id<Node> toNodeId = Id.create(stopTime.stop_id, Node.class);
//				double arrivalTime = Time.parseTime(String.valueOf(stopTime.arrival_time));
//				String toShapeDist = "";
//				Coord toShapeCoord = null;
//				// Prepare the replacing with shaped links
//				if(createShapedNetwork){
//					if(!this.alternativeStationToShapeAssignment){
//						toShapeDist = String.valueOf(stopTime.shape_dist_traveled);
//					}else{
//						toShapeCoord = network.getNodes().get(toNodeId).getCoord();
//					}						
//				}
//				if(fromNodes.containsKey(fromNodeId)){
//					if(!(fromNodes.get(fromNodeId)).contains(toNodeId)){
//						addLink = true;
//					}
//				}else{
//					addLink = true;
//					fromNodes.put(fromNodeId, new ArrayList<Id<Node>>());
//				}
//				if(!(fromNodes.containsKey(toNodeId))){
//					fromNodes.put(toNodeId, new ArrayList<Id<Node>>());
//				}
//				// No 0-Length Links
//				if(fromNodeId.equals(toNodeId)){
//					addLink = false;
//				}
//				// If the toNode belongs to a different trip, there should not be a link!
//				if(!usedTripId.equals(stopTime.trip_id)){
//					addLink = false;				
//				}
//				// for each stop should exist one dummy node with a link in to it (dL2_) and a link back to the road (dL1_).
//
//				Link link = null;
//				if(addLink){
//					double length = CoordUtils.calcDistance(nodes.get(fromNodeId).getCoord(), nodes.get(toNodeId).getCoord());
//					Double freespeed = freespeedKmPerHour/3.6;
//					if((length > 0.0) && (!Double.isInfinite(departureTime)) && (!Double.isInfinite(arrivalTime))){
//						freespeed = length/(arrivalTime - departureTime);
//						if(freespeed.isInfinite()){
//							freespeed = freespeedKmPerHour/3.6;
//							System.out.println("The Difference between ArrivalTime at one Stop " + ts.getFacilities().get(toNodeId).getName() + "(" + toNodeId + ") and DepartureTime at the previous Stop " + ts.getFacilities().get(fromNodeId).getName() + "(" + fromNodeId + ") is 0. That leads to high freespeeds.");
//						}
//					}
//					link = network.getFactory().createLink(Id.create(i++, Link.class), nodes.get(fromNodeId), nodes.get(toNodeId));
//					link.setLength(length);
//					link.setFreespeed(freespeed);
//					link.setCapacity(capacity);
//					link.setNumberOfLanes(numLanes);
//					network.addLink(link);
//					// Change the linktype to pt
//					Set<String> modes = new HashSet<String>();
//					modes.add(TransportMode.pt);
//					link.setAllowedModes(modes);
//					fromNodes.get(fromNodeId).add(toNodeId);
//					// Prepare the replacing with shaped links
//					if(createShapedNetwork){
//						if(!alternativeStationToShapeAssignment){
//							String[] shapeInfos = new String[3];
//							shapeInfos[0] = usedTripId;
//							shapeInfos[1] = fromShapeDist;
//							shapeInfos[2] = toShapeDist;
//							this.shapedLinkIds.put(link.getId(), shapeInfos);
//						}else{
//							String[] shapeInfos = new String[5];
//							shapeInfos[0] = usedTripId;
//							shapeInfos[1] = String.valueOf(fromShapeCoord.getX());
//							shapeInfos[2] = String.valueOf(fromShapeCoord.getY());
//							shapeInfos[3] = String.valueOf(toShapeCoord.getX());
//							shapeInfos[4] = String.valueOf(toShapeCoord.getY());
//							this.shapedLinkIdsCoordinate.put(link.getId(), shapeInfos);
//						}							
//					}						
//				}									
//			}		
//		}
//	}

//	private void createDummyNodeAndLinks(Id<Node> toNodeId, double capacity,
//			int numLanes, Network network, Map<Id<Node>, ? extends Node> nodes) {
//		Id<Node> dummyId = Id.create("dN_" + toNodeId, Node.class);
//		if(!(network.getNodes().containsKey(dummyId))){
//			NodeImpl n = new NodeImpl(dummyId);
//			n.setCoord(new Coord(nodes.get(toNodeId).getCoord().getX() + 1, nodes.get(toNodeId).getCoord().getY() + 1));
//			network.addNode(n);
//			double length = 50;
//			Link link = network.getFactory().createLink(Id.create("dL1_" + toNodeId, Link.class), n, nodes.get(toNodeId));
//			link.setLength(length);
//			link.setFreespeed(1000);
//			link.setCapacity(capacity);
//			link.setNumberOfLanes(numLanes);
//			network.addLink(link);
//			// Change the linktype to pt
//			Set<String> modes = new HashSet<String>();
//			modes.add(TransportMode.pt);
//			link.setAllowedModes(modes);
//			// Backwards
//			Link link2 = network.getFactory().createLink(Id.create("dL2_" + toNodeId, Link.class), nodes.get(toNodeId), n);
//			link2.setLength(length);
//			link2.setFreespeed(1000);
//			link2.setCapacity(capacity);
//			link2.setNumberOfLanes(numLanes);
//			network.addLink(link2);
//			link2.setAllowedModes(modes);
//		}
//	}

//
//	private void createTransitVehicles(){
//		for(String s: vehicleIdsAndTypes.keySet()){
//			// TYPE
//			VehicleType vt;
//            int gtfsVehicleType = vehicleIdsAndTypes.get(s);
//            vt = getVehicleType(gtfsVehicleType);
//
//            // Vehicle
//			Vehicle v = scenario.getTransitVehicles().getFactory().createVehicle(Id.create(s, Vehicle.class), vt);
//			scenario.getTransitVehicles().addVehicle(v);
//		}
//	}
//
//    private VehicleType getVehicleType(int gtfsVehicleType) {
//        VehicleType vt;
//        switch(gtfsVehicleType) {
//        // These are route types as specified by gtfs
//        case 0: vt = vehicleType(Id.create("type-tram", VehicleType.class)); break;
//        case 1: vt = vehicleType(Id.create("type-subway", VehicleType.class)); break;
//        case 2: vt = vehicleType(Id.create("type-rail", VehicleType.class)); break;
//        case 3: vt = vehicleType(Id.create("type-bus", VehicleType.class)); break;
//        case 4: vt = vehicleType(Id.create("type-ferry", VehicleType.class)); break;
//        case 5: vt = vehicleType(Id.create("type-cablecar", VehicleType.class)); break;
//        case 6: vt = vehicleType(Id.create("type-gondola", VehicleType.class)); break;
//        case 7: vt = vehicleType(Id.create("type-funicular", VehicleType.class)); break;
//        default: vt = vehicleType(Id.create("type-unidentified", VehicleType.class));
//        }
//        return vt;
//    }
//
//    private VehicleType vehicleType(Id<VehicleType> type) {
//        VehicleType vt = scenario.getTransitVehicles().getVehicleTypes().get(type);
//        if (vt == null) {
//            VehicleCapacity vc = scenario.getTransitVehicles().getFactory().createVehicleCapacity();
//            vc.setSeats(50);
//            vc.setStandingRoom(50);
//            vt = scenario.getTransitVehicles().getFactory().createVehicleType(type);
//            vt.setCapacity(vc);
//            vt.setLength(5);
//            scenario.getTransitVehicles().addVehicleType(vt);
//        }
//        return vt;
//    }
//
//    private int getWeekday(long date) {
//		int year = (int)(date/10000);
//		int month = (int)((date - year*10000)/100);
//		int day = (int)((date-month*100-year*10000));
//		Calendar cal = new GregorianCalendar(year,month-1,day);
//		int weekday = cal.get(Calendar.DAY_OF_WEEK)-1;
//		if(weekday < 1){
//			return 7;
//		}else{
//			return weekday;
//		}		
//	}
//
//
//
//
	// OPTIONAL SHAPE STUFF

//	private void convertShapes(Map<String, Map<Integer, com.conveyal.gtfs.model.Shape>> shapes2){
////		int shapeIdIndex = shapesSource.getContentIndex("shape_id");
////		int shapeLatIndex = shapesSource.getContentIndex("shape_pt_lat");
////		int shapeLonIndex = shapesSource.getContentIndex("shape_pt_lon");
////		int shapeDistIndex = shapesSource.getContentIndex("shape_dist_traveled");
//		for(Entry<String, Map<Integer, com.conveyal.gtfs.model.Shape>> entry: shapes2.entrySet()) {
//        		Iterator<com.conveyal.gtfs.model.Shape> it = entry.getValue().values().iterator();
//        		com.conveyal.gtfs.model.Shape shape = it.next();
//        		
//        		String oldShapeId = shape.shape_id;
//        		List<String[]> shapes = new ArrayList<String[]>();
//        		for(;it.hasNext();) {
//        			String shapeId = shape.shape_id;
//        			String shapeLat = String.valueOf(shape.shape_pt_lat);
//        			String shapeLon = String.valueOf(shape.shape_pt_lon);
//        			String shapeDist;
//        			if(shape.shape_dist_traveled == Double.NaN){
//        				shapeDist = "Alternative Station To Shape Assignment Is Used";
//        			}else{
//        				shapeDist = String.valueOf(shape.shape_dist_traveled);
//        			}
//        			if(oldShapeId.equals(shapeId)){
//        				String[] params = new String[3];
//        				params[0] = shapeDist;
//        				params[1] = shapeLon;
//        				params[2] = shapeLat;
//        				shapes.add(params);
//        			}else{
//        				this.shapes.put(Id.create(oldShapeId, Shape.class), shapes);
//        				oldShapeId = shapeId;
//        				shapes = new ArrayList<String[]>();
//        				String[] params = new String[3];
//        				params[0] = shapeDist;
//        				params[1] = shapeLon;
//        				params[2] = shapeLat;
//        				shapes.add(params);
//        			}
//        		}
//        		this.shapes.put(Id.create(oldShapeId, Shape.class), shapes);
//		}
//	}

//	private Map<Id<Link>,List<Coord>> assignShapesToLinks() {
//		Map<Id<Link>,List<Coord>> result = new HashMap<>();
//		Map<Id<Link>,String[]> linkIdInfos = null;
//		if(this.alternativeStationToShapeAssignment){
//			linkIdInfos = this.shapedLinkIdsCoordinate;			
//		}else{
//			linkIdInfos = this.shapedLinkIds;
//		}
//		for(Id<Link> linkId: linkIdInfos.keySet()){
//			String[] params = linkIdInfos.get(linkId);
//			if(params[2].isEmpty()){
//				System.out.println("There might be a problem with shape_dist_traveled field of the trip " + params[0]);
//			}
//			String tripId = params[0];
//			List<Coord> coord = new ArrayList<Coord>();
//			if(!this.alternativeStationToShapeAssignment){
//				Double shapeDistStart = Double.parseDouble(params[1].trim());
//				Double shapeDistEnd = Double.parseDouble(params[2].trim());
//				String shapeId = this.shapeIdToTripIdAssignments.get(tripId);
//				List<String[]> shapes = this.shapes.get(Id.create(shapeId, Shape.class));		
//				for(String[] shapeCoord: shapes){
//					double dist = Double.parseDouble(shapeCoord[0].trim());
//					if((shapeDistStart <= dist) && (shapeDistEnd >= dist)){
//						coord.add(transform.transform(new Coord(Double.parseDouble(shapeCoord[1]), Double.parseDouble(shapeCoord[2]))));
//					}
//				}
//			}else{
//				Coord fromCoord = new Coord(Double.parseDouble(params[1]), Double.parseDouble(params[2]));
//				Coord toCoord = new Coord(Double.parseDouble(params[3]), Double.parseDouble(params[4]));
//				String shapeId = this.shapeIdToTripIdAssignments.get(tripId);
//				List<String[]> shapes = this.shapes.get(Id.create(shapeId, Shape.class));
//				boolean add = false;
//				for(String[] shapeCoord: shapes){
//					Coord c = transform.transform(new Coord(Double.parseDouble(shapeCoord[1]), Double.parseDouble(shapeCoord[2])));
//					if(CoordUtils.calcDistance(c, fromCoord) <= this.toleranceInM){
//						add = true;						 
//					}else if(CoordUtils.calcDistance(c, toCoord) <= this.toleranceInM){
//						add = false;
//						coord.add(c);
//						break;
//					}
//					if(add){
//						coord.add(c);						
//					}
//				}
//			}
//			if(!coord.isEmpty()){
//				result.put(linkId, coord);
//			}else{
//				result.put(linkId, coord);
//				System.out.println("Couldn't find any shapes for Link " + linkId + ". This Link will not be shaped.");
//			}			
//		}
//		return result;
//	}
	
	private static class Shape {
		
	}
	
	private static class Trip {
		
	}

}
