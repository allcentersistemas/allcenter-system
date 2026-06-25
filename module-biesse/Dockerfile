# Build desde allcenter-system/: docker build -f module-biesse/Dockerfile .
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY module-biesse module-biesse
# El pom padre declara module-system; Maven exige que exista en el reactor
COPY module-system module-system
RUN mvn -pl module-biesse -am clean package -DskipTests -q

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/module-biesse/target/*-SNAPSHOT.jar /app/app.jar
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=America/Lima"
EXPOSE 8086
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
