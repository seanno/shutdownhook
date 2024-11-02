#!/bin/bash

SCRIPTDIR=`dirname $0`

# build toolbox

if [ "$1" != "skip_toolbox" ] ; then
	
	pushd $SCRIPTDIR/../toolbox
	mvn clean package install
	popd
fi

# build client

pushd $SCRIPTDIR/client
npm run build
popd

# build server

pushd $SCRIPTDIR/server
mvn clean package
popd



