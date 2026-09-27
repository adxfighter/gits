# syntax=docker/dockerfile:1.7
# Builds one backend service. Usage: docker build --build-arg MODULE=api|runner backend/
ARG MODULE=api

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY gits-core/pom.xml gits-core/
COPY gits-api/pom.xml gits-api/
COPY gits-runner/pom.xml gits-runner/
COPY gits-taskbank/pom.xml gits-taskbank/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl gits-${MODULE} -am dependency:go-offline -DexcludeReactor=true || true
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl gits-${MODULE} -am package -DskipTests \
    && cp gits-${MODULE}/target/gits-${MODULE}.jar /app.jar

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10010 --create-home gits
COPY --from=build /app.jar /opt/gits/app.jar
USER gits
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/opt/gits/app.jar"]
