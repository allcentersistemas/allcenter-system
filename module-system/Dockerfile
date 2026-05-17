FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY module-system module-system
# El pom padre declara module-biesse; Maven exige que exista en el reactor
COPY module-biesse module-biesse
RUN mvn -pl module-system -am clean package -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/module-system/target/*-SNAPSHOT.jar /app/app.jar
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
