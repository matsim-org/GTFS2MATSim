package org.matsim.contrib.gtfs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.testcases.MatsimTestUtils;

import java.nio.file.Path;
import java.time.LocalDate;

public class GtfsConverterTest {

    @RegisterExtension
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Test
    public void testConverterWithNonRegularCharacter(){

        LocalDate startDate = LocalDate.of(2021, 11, 21); //double check dates!
        LocalDate endDate = LocalDate.of(2021, 11, 21);

        Path gtfs = Path.of(utils.getPackageInputDirectory()+ "test_feed.zip");

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        GtfsConverter converter = GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setMergeStops(GtfsConverter.MergeGtfsStops.doNotMerge)
                .setStartDate(startDate)
                .setEndDate(endDate)
                .setTransform(TransformationFactory.getCoordinateTransformation("EPSG:25832", "EPSG:25832"))
                .setFeed(gtfs)
                .build();

        converter.convert();

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(utils.getOutputDirectory() + "transitSchedule.xml.gz");

        Config config = ConfigUtils.createConfig();
        config.transit().setTransitScheduleFile(utils.getOutputDirectory() + "transitSchedule.xml.gz");

        Scenario scenario1 = ScenarioUtils.loadScenario(config);
    }
}