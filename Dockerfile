# ─────────────────────────────────────────────────────────────────────────────
# EmployeeTrack Backend — Multi-stage Dockerfile for Render deployment
#
# Stage 1 (builder): Compiles and packages the Spring Boot JAR with Maven
# Stage 2 (runtime): Runs the JAR on a slim JRE image
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer-cache friendly)
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy only the fat JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Render injects PORT automatically; default 8080 for local testing
EXPOSE 8080

# Start with prod profile; Render provides env vars at runtime
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}", \
  "-jar", "app.jar"]
