FROM java:8-jre-alpine

RUN apk update && apk add --no-cache unzip
RUN adduser -D -h /home/ircbot -u 1013 ircbot
ADD target/ircbot-2.0-SNAPSHOT-bin.zip /usr/local/bin/ircbot.zip
RUN cd /usr/local/bin; unzip ircbot.zip

EXPOSE 8080
USER ircbot

ENTRYPOINT ["java", "-Dircbot.name=jenkins-admin", "-jar", "/usr/local/bin/ircbot-2.0-SNAPSHOT.jar"]
