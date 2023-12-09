#!/bin/sh

cd client
npm run build

cd ../../toolbox
mvn clean package install

cd ../dss/server
mvn clean package

cd ..
