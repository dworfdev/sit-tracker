# Stage 1: Build Application Binary
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal Production Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root system user for security compliance
RUN addgroup -S sitgroup && adduser -S situser -G sitgroup
USER situser:sitgroup

# Copy compiled JAR from builder stage.
# FIXED: was "*.jar" (wildcard). Spring Boot's repackage step also leaves an
# "original-app.jar" backup in target/, so a wildcard match here previously
# hit two files and failed the Docker COPY (multi-source copy requires a
# directory destination, not a single filename). pom.xml now pins
# <finalName>app</finalName>, so this copies exactly one deterministic file.
COPY --from=builder /build/target/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]