/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

/**
 * 
 */
package org.matsim.contrib.gtfs;

import java.time.LocalDate;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.pt.utils.CreateVehiclesForSchedule;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.VehicleWriterV1;

/**
 * @author  jbischoff
 * This is an example script that utilizes GTFS2MATSim and creates a pseudo network and vehicles using MATSim standard API functionality.
 */

public final class RunGTFS2MATSimExample {

	private RunGTFS2MATSimExample() {
	}

	public static void main(String[] args) {
	
		// this was tested for the latest VBB GTFS, available at 
		// http://www.vbb.de/de/article/fahrplan/webservices/datensaetze/1186967.html
		
		//input data
		String gtfsZipFile = "testing/gtfs_Berlin.zip";
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:25833");
		LocalDate date = LocalDate.parse("2020-11-02");
		Boolean runVehicleCirculation = true;
		Boolean overrideMinDelay = false;
		int minTurnOverTime = 10;
		
		//output files 
		String scheduleFile = "transitSchedule.xml.gz";
		String networkFile = "network.xml.gz";
		String transitVehiclesFile ="transitVehicles.xml.gz";
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		//Convert GTFS
		RunGTFS2MATSim.convertGTFSandAddToScenario(scenario,gtfsZipFile,date,ct,true);

		// copy late/early departures to have at complete schedule from ca. 0:00 to ca. 30:00 
		// Unfortunately I was having issues with this command. It gives me a fatal error.@gmarburger
//		TransitSchedulePostProcessTools.copyLateDeparturesToStartOfDay(scenario.getTransitSchedule(), 24 * 3600, "copied", false);
//		TransitSchedulePostProcessTools.copyEarlyDeparturesToFollowingNight(scenario.getTransitSchedule(), 6 * 3600, "copied");

		/** If runVehicleCirculation is set to true it will create vehicle circulations instead of unique vehicle Ids.
		 *  It was created here and not in RunGTFS2MATSim.java because it will modify not only the Transit-Schedule but also Network and Vehicles.
		 */
		if(runVehicleCirculation) CreateVehicleCirculation.create(scenario, minTurnOverTime, overrideMinDelay);
		
		//Write out network, vehicles and schedule
		new NetworkWriter(scenario.getNetwork()).write(networkFile);
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(scheduleFile);
		new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(transitVehiclesFile);
	}
}