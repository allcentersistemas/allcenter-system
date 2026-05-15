# Build: docker build --build-arg MODULE=module-employee -f docker/Dockerfile .
ARG MODULE=module-employee

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
ARG MODULE

COPY pom.xml .
COPY module-common-security module-common-security
COPY module-eureka module-eureka
COPY module-employee module-employee
COPY module-client module-client
COPY module-order module-order
COPY module-transport module-transport
COPY module-biesse module-biesse
COPY module-pale module-pale
COPY module-location module-location
COPY module-inventory module-inventory
COPY module-rm module-rm

RUN mvn -pl "${MODULE}" -am clean package -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app
ARG MODULE
COPY --from=build /build/${MODULE}/target/*-SNAPSHOT.jar /app/app.jar

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
