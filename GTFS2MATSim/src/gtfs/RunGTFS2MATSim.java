package gtfs;

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
	}
	System.out.println("done");
    }
}
