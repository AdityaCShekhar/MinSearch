# syntax=docker/dockerfile:1.7

FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress package -DskipTests \
    && cp target/minsearch-*.jar /workspace/application.jar

FROM eclipse-temurin:17-jre AS runtime

RUN groupadd --system minsearch \
    && useradd --system --gid minsearch minsearch \
    && mkdir -p /opt/minsearch /var/lib/minsearch/documents /var/lib/minsearch/index \
    && chown -R minsearch:minsearch /opt/minsearch /var/lib/minsearch

WORKDIR /opt/minsearch
COPY --from=build --chown=minsearch:minsearch /workspace/application.jar ./application.jar

USER minsearch
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=docker

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget --quiet --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/opt/minsearch/application.jar"]
