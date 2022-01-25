#!/bin/bash

java -cp ../target/evolve-1.0-SNAPSHOT-jar-with-dependencies.jar \
	 -Dloglevel=FINE \
	 com.shutdownhook.evolve.Network \
	 "$1-config.json"


