package org.matsim.contrib.gtfs;

import java.time.LocalDate;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

import com.conveyal.gtfs.GTFSFeed;

public class RunGTFS2MATSim {

    /**
     * Starts the conversion. If coord transformation is given, WGS84 is used as default.
     * 
     * @author NKuehnel
     * @param fromFile path of input file
     * @param toFile path to write to
     * @param date date to check for transit data. if null, current date of system is used
     * @param transformation coordination transformation for stops. if null, WGS84 is used 

     */
    public static void convertGtfs(String fromFile, String toFile, LocalDate date, CoordinateTransformation transformation) {

	
	GTFSFeed feed = GTFSFeed.fromFile(fromFile);
	
	System.out.println("Parsed trips: "+feed.trips.size());
	System.out.println("Parsed routes: "+feed.routes.size());
	System.out.println("Parsed stops: "+feed.stops.size());
	
	Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
	
	if(transformation == null) {
	    transformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.WGS84);
	}
	GtfsConverter converter = new GtfsConverter(feed, scenario, transformation);
	if(date!=null) {
	    converter.setDate(date);
	}
	converter.convert();
	
	System.out.println("Converted stops: " + scenario.getTransitSchedule().getFacilities().size());
	
	TransitScheduleWriter writer = new TransitScheduleWriter(scenario.getTransitSchedule());
	writer.writeFile(toFile);
	
	System.out.println("done");
    }
}
