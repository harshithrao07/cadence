# syntax=docker/dockerfile:1.6
# Build context is the repo root (see compose.yaml) so the shared cadence-events / cadence-messaging modules are visible.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY cadence-events ./cadence-events
COPY cadence-messaging/pom.xml ./cadence-messaging/pom.xml
COPY cadence-messaging/src/main ./cadence-messaging/src/main
COPY catalog-service/pom.xml ./catalog-service/pom.xml
COPY catalog-service/src ./catalog-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f cadence-events/pom.xml install \
 && mvn -B -f cadence-messaging/pom.xml -DskipTests install \
 && mvn -B -f catalog-service/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/catalog-service/target/*.jar /app/app.jar
EXPOSE 8084
ENTRYPOINT ["java","-jar","/app/app.jar"]
