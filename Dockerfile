# TODO: Bump to JDK11 and generate a JRE with jlink - https://hub.docker.com/_/eclipse-temurin#CreatingaJREusingjlink
FROM eclipse-temurin:8-jre-alpine

RUN apk update && apk add --no-cache unzip
RUN adduser -D -h /home/ircbot -u 1013 ircbot
COPY target/ircbot-2.0-SNAPSHOT-bin.zip /usr/local/bin/ircbot.zip
RUN cd /usr/local/bin; unzip ircbot.zip

# Add Tini
ENV TINI_VERSION v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-static /tini
RUN chmod +x /tini

EXPOSE 8080
USER ircbot

ENTRYPOINT [\
  "/tini", "--",\
  "/bin/sh","-c",\
  "java -Dircbot.name=jenkins-admin -jar /usr/local/bin/ircbot-2.0-SNAPSHOT.jar"]
