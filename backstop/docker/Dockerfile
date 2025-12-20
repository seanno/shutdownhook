
###
### BACKSTOP
###

FROM timbru31/java-node:21-krypton

RUN mkdir /opt/backstop

# +---------------+
# | Script (Ring) |
# +---------------+

RUN mkdir /opt/backstop/ring
COPY scripts/ring/package.json .
WORKDIR /opt/backstop/ring
RUN npm install
COPY scripts/ring/index.js .

# +----------------+
# | Script (Water) |
# +----------------+

RUN mkdir /opt/backstop/water
COPY scripts/water/package.json .
WORKDIR /opt/backstop/water
RUN npm install
COPY scripts/water/index.js .

# +----------+
# | Core App |
# +----------+

COPY target/backstop-1.0-SNAPSHOT.jar /opt/backstop
COPY docker/config-docker.json /opt/backstop

# +----+
# | Go |
# +----+

WORKDIR /opt/backstop
CMD ["java", "-cp", "backstop-1.0-SNAPSHOT.jar", "com.shutdownhook.backstop.App", "config-docker.json"]




