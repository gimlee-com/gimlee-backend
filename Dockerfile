# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
# Copy libs.versions.toml if it exists (it's in gradle/ as per file structure)
COPY gradle/libs.versions.toml gradle/

# Copy all modules
COPY gimlee-common gimlee-common
COPY gimlee-events gimlee-events
COPY gimlee-notifications gimlee-notifications
COPY gimlee-auth gimlee-auth
COPY gimlee-media-store gimlee-media-store
COPY gimlee-location gimlee-location
COPY gimlee-payments gimlee-payments
COPY gimlee-ads gimlee-ads
COPY gimlee-api gimlee-api

# Build the application
RUN ./gradlew :gimlee-api:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create a non-root user
RUN addgroup --system spring && adduser --system --group spring
USER spring:spring

# Copy the jar from build stage
COPY --from=build /app/gimlee-api/build/libs/*.jar app.jar

# Expose the application port
EXPOSE 12060

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
