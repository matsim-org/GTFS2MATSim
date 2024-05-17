package org.matsim.contrib.gtfs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This is the authoritative list of route_types in Gtfs.
 * Index matters. The names are not authoritative.
 */
public enum RouteType {	
	// Simple types
	TRAM(0, "tram"),
	SUBWAY(1, "subway"),
	RAIL(2, "rail"),
	BUS(3, "bus"),
	FERRY(4, "ferry"),
	CABLE_CAR(5, "cable_car"),
	GONDOLA(6, "gondola"),
	FUNICULAR(7, "funicular"),
	
	// Extended types
	RAILWAY_SERVICE(100, "Railway Service", RAIL),
	HIGH_SPEED_RAIL_SERVICE(101, "High Speed Rail Service", RAIL),
	LONG_DISTANCE_TRAINS(102, "Long Distance Trains", RAIL),
	INTER_REGIONAL_RAIL_SERVICE(103, "Inter Regional Rail Service", RAIL),
	CAR_TRANSPORT_RAIL_SERVICE(104, "Car Transport Rail Service", RAIL),
	SLEEPER_RAIL_SERVICE(105, "Sleeper Rail Service", RAIL),
	REGIONAL_RAIL_SERVICE(106, "Regional Rail Service", RAIL),
	TOURIST_RAILWAY_SERVICE(107, "Tourist Railway Service", RAIL),
	RAIL_SHUTTLE_WITHIN_COMPLEX(108, "Rail Shuttle (Within Complex)", RAIL),
	SUBURBAN_RAILWAY(109, "Suburban Railway", RAIL),
	REPLACEMENT_RAIL_SERVICE(110, "Replacement Rail Service", RAIL),
	SPECIAL_RAIL_SERVICE(111, "Special Rail Service", RAIL),
	LORRY_TRANSPORT_RAIL_SERVICE(112, "Lorry Transport Rail Service", RAIL),
	ALL_RAIL_SERVICES(113, "All Rail Services", RAIL),
	CROSS_COUNTRY_RAIL_SERVICE(114, "Cross-Country Rail Service", RAIL),
	VEHICLE_TRANSPORT_RAIL_SERVICE(115, "Vehicle Transport Rail Service	", RAIL),
	RACK_AND_PINION_RAILWAY(116, "Rack and Pinion Railway", RAIL),
	ADDITIONAL_RAIL_SERVICE(117, "Additional Rail Service", RAIL),
	
	COACH_SERVICE(200, "Coach Service", BUS),
	INTERNATIONAL_COACH_SERVICE(201, "International Coach Service", BUS),
	NATIONAL_COACH_SERVICE(202, "National Coach Service", BUS),
	SHUTTLE_COACH_SERVICE(203, "Shuttle Coach Service", BUS),
	REGIONAL_COACH_SERVICE(204, "Regional Coach Service", BUS),
	SPECIAL_COACH_SERVICE(205, "Special Coach Service", BUS),
	SIGHTSEEING_COACH_SERVICE(206, "Sightseeing Coach Service", BUS),
	TOURIST_COACH_SERVICE(207, "Tourist Coach Service", BUS),
	COMMUTER_COACH_SERVICE(208, "Commuter Coach Service", BUS),
	ALL_COACH_SERVICES(209, "All Coach Services", BUS),
	
	SUBURBAN_RAILWAY_SERVICE(300, "Suburban Railway Service", RAIL),
	
	URBAN_RAILWAY_SERVICE(400, "Urban Railway Service", SUBWAY),
	METRO_SERVICE(401, "Metro Service", SUBWAY),
	UNDERGROUND_SERVICE(402, "Underground Service", SUBWAY),
	URBAN_RAILWAY_SERVICE_2(403, "Urban Railway Service", SUBWAY),
	ALL_URBAN_RAILWAY_SERVICES(404, "All Urban Railway Services", SUBWAY),
	MONORAIL(405, "Monorail", SUBWAY),
	
	METRO_SERVICE_2(500, "Metro Service", SUBWAY),
	
	UNDERGROUND_SERVICE_2(600, "Underground Service", SUBWAY),
	
	BUS_SERVICE(700, "Bus Service", BUS),
	REGIONAL_BUS_SERVICE(701, "Regional Bus Service", BUS),
	EXPRESS_BUS_SERVICE(702, "Express Bus Service", BUS),
	STOPPING_BUS_SERVICE(703, "Stopping Bus Service", BUS),
	LOCAL_BUS_SERVICE(704, "Local Bus Service", BUS),
	NIGHT_BUS_SERVICE(705, "Night Bus Service", BUS),
	POST_BUS_SERVICE(706, "Post Bus Service", BUS),
	SPECIAL_NEEDS_BUS(707, "Special Needs Bus", BUS),
	MOBILITY_BUS_SERVICE(708, "Mobility Bus Service", BUS),
	MOBILITY_BUS_SERVICE_FOR_REGISTERED_DISABLED(709, "Mobility Bus for Registered Disabled", BUS),
	SIGHTSEEING_BUS(710, "Sightseeing Bus", BUS),
	SHUTTLE_BUS(711, "Shuttle Bus", BUS),
	SCHOOL_BUS(712, "School Bus", BUS),
	SCHOOL_AND_PUBLIC_SERVICE_BUS(713, "School and Public Service Bus", BUS),
	RAIL_REPLACEMENT_BUS_SERVICE(714, "Rail Replacement Bus Service", BUS),
	DEMAND_AND_RESPONSE_BUS_SERVICE(715, "Demand and Response Bus Service", BUS),
	ALL_BUS_SERVICES(716, "All Bus Services", BUS),
	
	TROLLEYBUS_SERVICE(800, "Trolleybus Service", BUS),
	
	TRAM_SERVICE(900, "Tram Service", TRAM),
	CITY_TRAM_SERVICE(901, "City Tram Service", TRAM),
	LOCAL_TRAM_SERVICE(902, "Local Tram Service", TRAM),
	REGIONAL_TRAM_SERVICE(903, "Regional Tram Service", TRAM),
	SIGHTSEEING_TRAM_SERVICE(904, "Sightseeing Tram Service", TRAM),
	SHUTTLE_TRAM_SERVICE(905, "Shuttle Tram Service", TRAM),
	ALL_TRAM_SERVICE(906, "All Tram Services", TRAM),
	OTHER_TRAM_SERVICE(907, "Other Tram Services", TRAM),

	WATER_TRANSPORT_SERVICE(1000, "Water Transport Service", FERRY),
	INTERNATIONAL_CAR_FERRY_SERVICE(1001, "International Car Ferry Service", FERRY),
	NATIONAL_CAR_FERRY_SERVICE(1002, "National Car Ferry Service", FERRY),
	REGIONAL_CAR_FERRY_SERVICE(1003, "Regional Car Ferry Service", FERRY),
	LOCAL_CAR_FERRY_SERVICE(1004, "Local Car Ferry Service", FERRY),
	INTERNATIONAL_PASSENGER_FERRY_SERVICE(1005, "International Passenger Ferry Service", FERRY),
	NATIONAL_PASSENGER_FERRY_SERVICE(1006, "National Passenger Ferry Service", FERRY),
	REGIONAL_PASSENGER_FERRY_SERVICE(1007, "Regional Passenger Ferry Service", FERRY),
	LOCAL_PASSENGER_FERRY_SERVICE(1008, "Local Passenger Ferry Service", FERRY),
	POST_BOAT_SERVICE(1009, "Post Boat Service", FERRY),
	TRAIN_FERRY_SERVICE(1010, "Train Ferry Service", FERRY),
	ROAD_LINK_FERRY_SERVICE(1011, "Road-Link Ferry Service", FERRY),
	AIRPORT_LINK_FERRY_SERVICE(1012, "Airport-Link Ferry Service", FERRY),	 
	CAR_HIGH_SPEED_FERRY_SERVICE(1013, "Car High-Speed Ferry Service", FERRY),
	PASSENGER_HIGH_SPEED_FERRY_SERVICE(1014, "Passenger High-Speed Ferry Service", FERRY),
	SIGHTSEEING_BOAT_SERVICE(1015, "Sightseeing Boat Service", FERRY),
	SCHOOL_BOAT(1016, "School Boat", FERRY),
	CABLE_DRAWN_BOAT_SERVICE(1017, "Cable-Drawn Boat Service", FERRY),
	RIVER_BUS_SERVICE(1018, "River Bus Service", FERRY),
	SCHEDULED_FERRY_SERVICE(1019, "Scheduled Ferry Service", FERRY),
	SHUTTEL_FERRY_SERVICE(1020, "Shuttle Ferry Service", FERRY),
	ALL_WATER_TRANSPORT_SERVICES(1021, "All Water Transport Services", FERRY),
	
	AIR_SERVICE(1100, "Air Service"),
	INTERNATIONAL_AIR_SERVICE(1101, "International Air Service", AIR_SERVICE),
	DOMESTIC_AIR_SERVICE(1102, "Domestic Air Service", AIR_SERVICE),
	INTERCONTINENTAL_AIR_SERVICE(1103, "Intercontinental Air Service", AIR_SERVICE),
	DOMESTIC_SCHEDULED_SIR_SERVICE(1104, "Domestic Scheduled Air Service", AIR_SERVICE),
	SHUTTLE_AIR_SERVICE(1105, "Shuttle Air Service", AIR_SERVICE),
	INTERCONTINENTAL_CHARTER_AIR_SERVICE(1106, "Intercontinental Charter Air Service", AIR_SERVICE),
	INTERNATIONAL_CHARTER_AIR_SERVICE(1107, "International Charter Air Service", AIR_SERVICE),
	ROUND_TRIP_CHARTER_AIR_SERVICE(1108, "Round-Trip Charter Air Service", AIR_SERVICE),
	SIGHTSEEING_AIR_SERVICE(1109, "Sightseeing Air Service", AIR_SERVICE),
	HELICOPTER_AOR_SERVICE(1110, "Helicopter Air Service", AIR_SERVICE),
	DOMESTIC_CHARTER_AIR_SERVICE(1111, "Domestic Charter Air Service", AIR_SERVICE),
	SCHENGEN_AREA_AIR_SERVICE(1112, "Schengen-Area Air Service", AIR_SERVICE),
	AIRSHIP_SERVICE(1113, "Airship Service", AIR_SERVICE),
	ALL_AIR_SERVICE(1114, "All Air Services", AIR_SERVICE),
	
	FERRY_SERVICE(1200, "Ferry Service", FERRY),
	
	TELECABIN_SERVICE(1300, "Telecabin Service", GONDOLA),
	TELECABIN_SERVICE_2(1301, "Telecabin Service", GONDOLA),
	CABLE_CAR_SERVICE(1302, "Cable Car Service", GONDOLA),
	ELEVATOR_SERVICE(1303, "Elevator Service", GONDOLA),
	CHAIRL_LIFT_SERVICE(1304, "Chair Lift Service", GONDOLA),
	DRAG_LIFT_SERVICE(1305, "Drag Lift Service", GONDOLA),
	SMALL_TELECABIN_SERVICE(1306, "Small Telecabin Service", GONDOLA),
	ALL_TELECABIN_SERVICE(1307, "All Telecabin Services", GONDOLA),
	
	FUNICULAR_SERVICE(1400, "Funicular Service", FUNICULAR),
	FUNICULAR_SERVICE_2(1401, "Funicular Service", FUNICULAR),
	ALL_FUNICULAR_SERVICE(1402, "All Funicular Service", FUNICULAR),
	
	TAXI_SERVICE(1500, "Taxi Service"),
	COMMUNAL_TAXI_SERVICE(1501, "Communal Taxi Service", TAXI_SERVICE),
	WATER_TAXI_SERVICE(1502, "Water Taxi Service", TAXI_SERVICE),
	RAIL_TAXI_SERVICE(1503, "Rail Taxi Service", TAXI_SERVICE),
	BIKE_TAXI_SERVICE(1504, "Bike Taxi Service", TAXI_SERVICE),
	LICENSED_TAXI_SERVICE(1505, "Licensed Taxi Service", TAXI_SERVICE),
	PRIVATE_HIRE_SERVICE_VEHICLE(1506, "Private Hire Service Vehicle", TAXI_SERVICE),
	ALL_TAXI_SERVICES(1507, "All Taxi Services", TAXI_SERVICE),
	
	SELF_DRIVE(1600, "Self Drive"),
	HIRE_CAR(1601, "Hire Car", SELF_DRIVE),
	HIRE_VAN(1602, "Hire Van", SELF_DRIVE),
	HIRE_MOTORBIKE(1603, "Hire Motorbike", SELF_DRIVE),
	HIRE_CYCLE(1604, "Hire Cycle", SELF_DRIVE),
	
	MISCELLANEOUS_SERVICE(1700, "Miscellaneous Service"),
	CABLE_CAR_2(1701, "Cable Car", CABLE_CAR),
	HORSE_DRAWN_CARRIAGE(1702, "Horse-drawn Carriage", MISCELLANEOUS_SERVICE);
	
	private static final Map<Integer, RouteType> routeTypes = new HashMap<>();
	private final int code;
	private final String name;
	private final RouteType simpleRouteType;
	
	static {
		for (RouteType type : RouteType.values()) {
			routeTypes.put(type.code, type);
		}
	}
	
	RouteType(int code, String name, RouteType standardType) {
		this.code = code;
		this.name = name;
		this.simpleRouteType = standardType;
	}
	
	RouteType(int code, String name) {
		this.code = code;
		this.name = name;
		this.simpleRouteType = null;
	}
	
	public static Map<Integer, RouteType> getRouteTypes() {
		return Collections.unmodifiableMap(routeTypes);
	}

	public String getTypeName() {
		return name;
	}

	public int getCode() {
		return code;
	}

	public String getSimpleTypeName() {
		if (simpleRouteType == null) {
			return name;
		}
		return simpleRouteType.getTypeName();
	}
}