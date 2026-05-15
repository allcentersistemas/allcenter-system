# Etapa 1: Construcción del proyecto (Build stage)
FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /build
# Copiar los archivos necesarios para la compilación con Maven
COPY pom.xml .
COPY module-eureka/pom.xml module-eureka/
# Copiar el código fuente del módulo
COPY module-eureka/src module-eureka/src
# Compilar y empaquetar la aplicación, saltando los tests
RUN mvn -pl module-eureka -am clean package -DskipTests

# Etapa 2: Creación del entorno de ejecución personalizado (JRE Stage)
FROM eclipse-temurin:25-jdk-alpine AS jre-build
WORKDIR /jre-build
# Usar jlink para crear un JRE a medida que incluya los módulos de java.base y java.sql
RUN jlink \
    --add-modules java.base,java.sql \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /custom-jre

# Etapa 3: Imagen final (Final stage)
FROM alpine:latest
WORKDIR /app
# Copiar el JRE personalizado desde la etapa anterior
ENV JAVA_HOME=/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /custom-jre $JAVA_HOME
# Copiar el JAR de la aplicación desde la etapa de construcción
COPY --from=build /build/module-eureka/target/*.jar app.jar
EXPOSE ${PORT:-8761}
ENTRYPOINT ["java", "-jar", "app.jar"]