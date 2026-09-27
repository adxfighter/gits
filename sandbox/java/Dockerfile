# syntax=docker/dockerfile:1.7
# gits-sandbox-java: compiles and tests candidate code. One short-lived container per run.
# Run flags are mandatory and documented in README.md; never run this image without them.

FROM maven:3.9-eclipse-temurin-21 AS libs
WORKDIR /libs
COPY libs/pom.xml .
# --strict-checksums: fail the build if any artifact does not match the repository checksum
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q --strict-checksums dependency:copy-dependencies -DoutputDirectory=/opt/libs -DincludeScope=runtime \
    && mv /opt/libs/junit-platform-console-standalone-*.jar /opt/libs/junit-platform-console-standalone.jar \
    && sha256sum /opt/libs/*.jar > /opt/libs/SHA256SUMS

FROM eclipse-temurin:21-jdk-alpine
RUN adduser -D -H -u 10001 -s /sbin/nologin sandbox
COPY --from=libs /opt/libs/ /opt/libs/
COPY entrypoint.sh /opt/gits/entrypoint.sh
RUN chmod 0555 /opt/gits/entrypoint.sh && chmod -R a-w /opt/libs /opt/gits
USER 10001
WORKDIR /work
ENV LANG=C.UTF-8
ENTRYPOINT ["/opt/gits/entrypoint.sh"]
