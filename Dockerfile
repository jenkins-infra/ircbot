FROM eclipse-temurin:18.0.2.1_1-jre-alpine

RUN adduser -D -h /home/ircbot -u 1013 ircbot

ARG APP_NAME=ircbot-2.0-SNAPSHOT
COPY "target/${APP_NAME}-bin.zip" /usr/local/bin/ircbot.zip

## Always use the latest "unzip" package version.
## TODO: change assembly from zip to a jar file to get rid of the "unzip" step here (no need for ZIP)
# hadolint ignore=DL3018
RUN apk add --no-cache unzip \
  && unzip /usr/local/bin/ircbot.zip -d /usr/local/bin \
  && rm -f ircbot.zip

# Add Tini
ENV TINI_VERSION v0.19.0
ADD "https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-static" /tini
RUN chmod a+x /tini

EXPOSE 8080
USER ircbot

# Persist the variable in the image as an env. variable
ENV APP_NAME="${APP_NAME}"
ENTRYPOINT [\
  "/tini", "--",\
  "/bin/sh","-c",\
  "java -Dircbot.name=jenkins-admin -jar /usr/local/bin/${APP_NAME}.jar"]
