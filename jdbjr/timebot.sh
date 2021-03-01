#!/bin/sh
java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=12345 TimeBot
