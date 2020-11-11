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
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.vehicles.MatsimVehicleWriter;

/**
 * @author  gmarburger
 * This is an example script that utilizes GTFS2MATSim, creates a Scenario and thus creates vehicle circulations inside this scenario. Vehicle circulations are only within the
 * same TransitLine.
 * Since I had problems running copyLateDeparturesToStartOfDay / copyEarlyDeparturesToFollowingNight I commented these out.
 */

public class RunGTFS2MATSimExampleRunVehicleCirculation {

	public static void main(String[] args) {
		//input data
		String gtfsZipFile = "testing/gtfs_Berlin.zip";
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:25833");
		LocalDate date = LocalDate.parse("2020-11-02");
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


		CreateVehicleCirculation.create(scenario, minTurnOverTime, overrideMinDelay);
		
		//Write out network, vehicles and schedule
		new NetworkWriter(scenario.getNetwork()).write(networkFile);
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(scheduleFile);
		new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(transitVehiclesFile);
	}

}
