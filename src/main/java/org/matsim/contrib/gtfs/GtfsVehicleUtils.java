package org.matsim.contrib.gtfs;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;

public class GtfsVehicleUtils {

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
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		VehicleCapacity vc = scenario.getTransitVehicles().getFactory().createVehicleCapacity();
		vc.setSeats(50);
		vc.setStandingRoom(50);
		VehicleType vt = scenario.getTransitVehicles().getFactory().createVehicleType(type);
		vt.setCapacity(vc);
		vt.setLength(5);
		return vt;
	}



//	private void createTransitVehicles(){
//		for(String s: vehicleIdsAndTypes.keySet()){
//			// TYPE
//			VehicleType vt;
//			int gtfsVehicleType = vehicleIdsAndTypes.get(s);
//			vt = getVehicleType(gtfsVehicleType);
//
//			// Vehicle
//			Vehicle v = scenario.getTransitVehicles().getFactory().createVehicle(Id.create(s, Vehicle.class), vt);
//			scenario.getTransitVehicles().addVehicle(v);
//		}
//	}



}
