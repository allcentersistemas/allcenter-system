FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

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

RUN mvn -pl module-eureka -am clean package -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/module-eureka/target/*-SNAPSHOT.jar /app/app.jar
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
