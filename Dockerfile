FROM ubuntu

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update -y
RUN apt-get install -y \
        openjdk-11-jre-headless

COPY ./build/libs/*-all.jar /app/EspoGmailSync.jar

ENV JVM_MEM=2048M

CMD ["sh", "-c", "/usr/bin/java -Xmx${JVM_MEM} -jar /app/EspoGmailSync.jar"]