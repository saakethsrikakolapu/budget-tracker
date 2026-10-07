# Multi-stage build: compile with the full JDK, then run on a smaller Java runtime image.
# Only the final stage ends up in the image that gets deployed.

# ---- Stage 1: build the jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy the build files first and download dependencies. Docker caches this layer, so later
# builds that only change source code skip re-downloading everything.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests already run in CI (they need Docker for Testcontainers, which isn't available here).
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: run it ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Don't run as root: if the app were ever compromised, the attacker gets a powerless user.
RUN useradd --system --uid 10001 app
USER app

COPY --from=build /app/target/*.jar app.jar

# Fit in a 512 MB free instance: cap the heap at 60% of the container's memory, use the
# low-overhead serial garbage collector, and give each thread a smaller stack.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
