[![Build Status](https://travis-ci.org/matsim-org/GTFS2MATSim.svg?branch=master)](https://travis-ci.org/matsim-org/GTFS2MATSim)

# GTFS2MATSim

This repository contains conversion code to convert GTFS data into a MATSim transit schedule format using [conveyal gtfs-lib](https://github.com/conveyal/gtfs-lib). 

To use it, follow the following steps:
1) Check out this repository using an IDE.
2) Run [RunGTFS2MATSim](https://github.com/matsim-org/GTFS2MATSim/blob/master/src/main/java/org/matsim/contrib/gtfs/RunGTFS2MATSim.java) to get only a schedule conversion or [RunGTFS2MATSimExample] (https://github.com/matsim-org/GTFS2MATSim/blob/master/src/main/java/org/matsim/contrib/gtfs/RunGTFS2MATSimExample.java) if you also require a vehicles file and an additional PseudoNetwork.
