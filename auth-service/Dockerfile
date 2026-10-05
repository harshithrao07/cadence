# syntax=docker/dockerfile:1.6
# Build context is the repo root (see compose.yaml) so the shared cadence-events module is visible.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY cadence-events ./cadence-events
COPY auth-service/pom.xml ./auth-service/pom.xml
COPY auth-service/src ./auth-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f cadence-events/pom.xml install \
 && mvn -B -f auth-service/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/auth-service/target/*.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["java","-jar","/app/app.jar"]
