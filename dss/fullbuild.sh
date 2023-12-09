#!/bin/sh

cd client
npm run build
cd ..

if [ "$1" != "skip_toolbox" ] ; then
	
	cd ../toolbox
	mvn clean package install
	cd ../dss
	
fi

cd server
mvn clean package

cd ..
