#!/bin/sh

java \
	-cp ../target/shl-1.0-SNAPSHOT-jar-with-dependencies.jar \
	com.shutdownhook.shl.App \
	demo-config.json
