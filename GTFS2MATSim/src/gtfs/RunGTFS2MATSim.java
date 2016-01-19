package gtfs;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import com.conveyal.gtfs.GTFSFeed;

public class RunGTFS2MATSim {

    /**
     * Starts the conversion.
     * 
     * @author NKuehnel
     * @param filePath
     *            the path to GTFS file as .zip
     */
    public static void main(String[] filePath) {

	if (filePath != null && filePath.length > 0) {
	    GTFSFeed feed = GTFSFeed.fromFile(filePath[0]);
	    
	    System.out.println("Parsed trips: "+feed.trips.size());
	    System.out.println("Parsed routes: "+feed.routes.size());
	    System.out.println("Parsed stops: "+feed.stops.size());
	    
	    CoordinateTransformation coordinateTransformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.WGS84);
	    Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
	    GtfsConverter converter = new GtfsConverter(feed, scenario, coordinateTransformation);
	    converter.convert();
	    
	    System.out.println("Converted stops: " + scenario.getTransitSchedule().getFacilities().size());
	}
	
	System.out.println("done");
    }
}
