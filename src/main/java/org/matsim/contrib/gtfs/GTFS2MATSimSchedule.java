package org.matsim.contrib.gtfs;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.gtfs.RunGTFS2MATSim;
import org.matsim.contrib.gtfs.TransitSchedulePostProcessTools;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.pt.utils.CreateVehiclesForSchedule;
import org.matsim.vehicles.VehicleWriterV1;

import java.time.LocalDate;

/*
 * https://github.com/matsim-org/GTFS2MATSim/blob/master/src/main/java/org/matsim/contrib/gtfs/RunGTFS2MATSimExample.java
 */
public class GTFS2MATSimSchedule {

    private static final String gtfs_zip_file = "C:/Users/Amit Agarwal/Google Drive/iitr_amit.ce.iitr/projects/data/GTFS/Delhi_PT/GTFS.zip";
    private static final String matsimFilesDir = "C:/Users/Amit Agarwal/Google Drive/iitr_amit.ce.iitr/projects/data/GTFS/Delhi_PT/matsimFiles/";
    private static final CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:32643");
    private static final String matsim_network_file = "C:/Users/Amit Agarwal/Google Drive/iitr_amit.ce.iitr/projects/data/Delhi_Trilokpuri/matsimFiles/Delhi_matsim_network_fromPBF_insideDelhi_upto_level6.xml.gz";

    public static void main(String[] args) {
        String scheduleFile = matsimFilesDir+"/transitSchedule.xml.gz";
        String networkFile = matsimFilesDir+"/transit_network.xml.gz";
        String transitVehiclesFile = matsimFilesDir+"/transit_vehicles.xml.gz";

        LocalDate date = LocalDate.parse("2020-03-01");
        RunGTFS2MATSim.convertGtfs(gtfs_zip_file, scheduleFile, date, ct, false);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(scheduleFile);

        // copy late/early departures to have at complete schedule from ca. 0:00 to ca. 30:00
        TransitSchedulePostProcessTools.copyLateDeparturesToStartOfDay(scenario.getTransitSchedule(), 24 * 3600, "copied", false);
        TransitSchedulePostProcessTools.copyEarlyDeparturesToFollowingNight(scenario.getTransitSchedule(), 6 * 3600, "copied");

        //if neccessary, parse in an existing network file here:
		new MatsimNetworkReader(scenario.getNetwork()).readFile(matsim_network_file);

        //Create a network around the schedule
        new CreatePseudoNetwork(scenario.getTransitSchedule(),scenario.getNetwork(),"pt_").createNetwork();

        //Create simple transit vehicles
        new CreateVehiclesForSchedule(scenario.getTransitSchedule(), scenario.getTransitVehicles()).run();

        //Write out network, vehicles and schedule
        new NetworkWriter(scenario.getNetwork()).write(networkFile);
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(scheduleFile);
        new VehicleWriterV1(scenario.getTransitVehicles()).writeFile(transitVehiclesFile);

    }

}
