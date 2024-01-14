@echo off

set CFG=%1
if [%CFG%] == [] set CFG=config.json 

java ^
  -cp dss-server-1.0-SNAPSHOT.jar ^
  com.shutdownhook.dss.server.App ^
  %CFG% 
