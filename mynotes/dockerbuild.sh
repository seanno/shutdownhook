#!/bin/bash

SCRIPTDIR=`dirname $0`

pushd $SCRIPTDIR
docker build -t seanno/shutdownhook:explain -f docker/Dockerfile .
docker push seanno/shutdownhook:explain
popd



