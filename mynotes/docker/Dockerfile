
###
### EXPLAIN 
###

FROM eclipse-temurin:21

RUN mkdir /opt/explain
COPY server/target/mynotes-1.0-SNAPSHOT.jar /opt/explain
COPY docker/config-docker.json /opt/explain
COPY docker/logging-docker.json /opt/explain

EXPOSE 80

CMD ["java", "-cp", "/opt/explain/mynotes-1.0-SNAPSHOT.jar", "com.shutdownhook.mynotes.App", "/opt/explain/config-docker.json"]




