#!/bin/bash

SCRIPTDIR=`dirname $0`

pushd $SCRIPTDIR
docker build -t seanno/backstop:nolan -f docker/Dockerfile .
docker push seanno/backstop:nolan
popd



