
CFG=$1
if [ -z $CFG ]; then
	CFG=config.json
fi

nohup java \
  -cp dss-server-1.0-SNAPSHOT.jar \
  com.shutdownhook.dss.server.App \
  $CFG &
