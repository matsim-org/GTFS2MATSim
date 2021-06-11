package org.matsim.contrib.gtfs;

import java.time.LocalDate;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.IdentityTransformation;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.pt.utils.CreateVehiclesForSchedule;

import com.conveyal.gtfs.GTFSFeed;

/**
 * @author NKuehnel
 */
public class RunGTFS2MATSim {

    /**
     * Starts the conversion.
     * 
     * @param fromFile path of input file
     * @param toFile path to write to
     * @param date date to check for transit data
     * @param transformation coordination transformation for stops
	 * @param useExtendedRouteTypes transfer extended route types to MATSim schedule
     */
    public static void convertGtfs(String fromFile, String toFile, LocalDate date, CoordinateTransformation transformation, boolean useExtendedRouteTypes) {
		GTFSFeed feed = GTFSFeed.fromFile(fromFile);

		feed.feedInfo.values().stream().findFirst().ifPresent(feedInfo -> {
			System.out.println("Feed start date: " + feedInfo.feed_start_date);
			System.out.println("Feed end date: " + feedInfo.feed_end_date);
		});

		System.out.println("Parsed trips: "+feed.trips.size());
		System.out.println("Parsed routes: "+feed.routes.size());
		System.out.println("Parsed stops: "+feed.stops.size());

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		GtfsConverter converter = GtfsConverter.newBuilder()
				.setScenario(scenario)
				.setTransform(transformation)
				.setFeed(feed)
				.setDate(date)
				.setUseExtendedRouteTypes(useExtendedRouteTypes)
				.build();

		converter.convert();

		System.out.println("Converted stops: " + scenario.getTransitSchedule().getFacilities().size());

		TransitScheduleWriter writer = new TransitScheduleWriter(scenario.getTransitSchedule());
		writer.writeFile(toFile);

		System.out.println("Done.");
    }

	public static void convertGTFSandAddToScenario(Scenario scenario, String gtfsZip, LocalDate date, CoordinateTransformation coordinateTransformation, boolean createNetworkAndVehicles) {
		GTFSFeed feed = GTFSFeed.fromFile(gtfsZip);
		feed.feedInfo.values().stream().findFirst().ifPresent((feedInfo) -> {
			System.out.println("Feed start date: " + feedInfo.feed_start_date);
			System.out.println("Feed end date: " + feedInfo.feed_end_date);
		});
		GtfsConverter converter = GtfsConverter.newBuilder().setFeed(feed).setScenario(scenario).setTransform(coordinateTransformation).setUseExtendedRouteTypes(false).setDate(date).build();
		converter.convert();
		TransitSchedulePostProcessTools.copyLateDeparturesToStartOfDay(scenario.getTransitSchedule(), 86400.0, "copied", false);
		TransitSchedulePostProcessTools.copyEarlyDeparturesToFollowingNight(scenario.getTransitSchedule(), 21600.0, "copied");
		if (createNetworkAndVehicles) {
			(new CreatePseudoNetwork(scenario.getTransitSchedule(), scenario.getNetwork(), "pt_")).createNetwork();
			(new CreateVehiclesForSchedule(scenario.getTransitSchedule(), scenario.getTransitVehicles())).run();
		}
	}

	public static void main(String[] args) {
		String inputZipFile = args[0];
		String outputFile = args[1];
		String date = args[2];
		boolean useExtendedRouteTypes = Boolean.parseBoolean(args[3]);
		convertGtfs(inputZipFile, outputFile, LocalDate.parse(date), new IdentityTransformation(), useExtendedRouteTypes);
	}

}
