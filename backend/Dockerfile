# ---------------------------------------------------------------------------
# Back end image, for the containerised sanity-check stack.
#
# This is a production shaped build: the jar is compiled once and run on a JRE.
# Day-to-day development still runs from source (`make dev`), which gives
# restarts and a debugger that a container cannot.
# ---------------------------------------------------------------------------

# --- build stage -----------------------------------------------------------
# The official Maven image rather than ./mvnw: the wrapper exists so a host
# without Maven can build, and inside a container we already control the
# toolchain. Using it directly skips the wrapper's download of Maven itself.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies resolve from the POM alone, so copying it first means a source
# change does not invalidate the dependency layer. The BuildKit cache mount
# keeps ~/.m2 between builds as well, so a rebuild is seconds rather than
# minutes.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src/ src/
# Tests are not run here. They run in CI and via `make test`, where a failure is
# readable; baking them into the image build only makes it slow and the failure
# output harder to find.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests \
 && mv target/patient-service-*.jar /build/app.jar

# --- runtime stage ---------------------------------------------------------
# JRE, not JDK: the compiler and the build tooling have no business in a running
# image, and a smaller image is a smaller attack surface.
#
# Not the -alpine variant, tempting as the size is: Temurin publishes Alpine for
# linux/amd64 only, so it fails outright on an Apple Silicon or arm64 CI machine.
# This tag is multi-arch and ships wget, which the healthcheck below needs.
FROM eclipse-temurin:17-jre AS runtime

# Never run as root. If the process is ever compromised, it should not own the
# filesystem it is standing on.
RUN groupadd --system spring && useradd --system --gid spring --create-home spring
WORKDIR /app

COPY --from=build --chown=spring:spring /build/app.jar app.jar

USER spring
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=demo \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Compose gates the front end on this, so the UI never starts against a service
# that is not ready to answer.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1

# exec form via sh so JAVA_OPTS is expanded, while the JVM still becomes PID 1
# and therefore receives SIGTERM directly for a graceful shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
