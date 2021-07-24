FROM gradle:7.1.1-jdk16 AS BUILDER
COPY ./src /usr/src/mailsync/src
COPY build.gradle /usr/src/mailsync
COPY settings.gradle /usr/src/mailsync

WORKDIR /usr/src/mailsync

RUN gradle build

FROM adoptopenjdk/openjdk16:jre-16.0.1_9-alpine
RUN apk add --no-cache ca-certificates
COPY --from=BUILDER /usr/src/mailsync/build/libs/mailsync-all.jar /usr/jar/mailsync.jar

EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "/usr/jar/mailsync.jar" ]