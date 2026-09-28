# syntax=docker/dockerfile:1.7
# Builds one backend service: docker build --build-arg MODULE=api|runner|taskbank --target api|runner|taskbank backend/
# (taskbank: also --build-arg JAR_SUFFIX=-exec — its runnable jar is the exec one)
ARG MODULE=api
ARG JAR_SUFFIX=

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
ARG JAR_SUFFIX
WORKDIR /src
COPY pom.xml .
COPY gits-core/pom.xml gits-core/
COPY gits-sandbox-client/pom.xml gits-sandbox-client/
COPY gits-api/pom.xml gits-api/
COPY gits-runner/pom.xml gits-runner/
COPY gits-taskbank/pom.xml gits-taskbank/
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl gits-${MODULE} -am package -DskipTests \
    && cp gits-${MODULE}/target/gits-${MODULE}${JAR_SUFFIX}.jar /app.jar

FROM eclipse-temurin:21-jre AS base
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10010 --create-home gits
COPY --from=build /app.jar /opt/gits/app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/opt/gits/app.jar"]

FROM base AS api
USER gits

# The task bank CLI (validate without sandbox, load, demo-seed): docker compose --profile tools run --rm taskbank ...
FROM base AS taskbank
USER gits

# The runner starts sandbox containers through the docker CLI and the mounted host socket.
FROM base AS runner
COPY --from=docker:29-cli /usr/local/bin/docker /usr/local/bin/docker
