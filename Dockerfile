# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first for dependency layer caching
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build jar
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for docker healthcheck
RUN apk add --no-cache curl && \
    addgroup -S orderflow && adduser -S orderflow -G orderflow

# Copy built artifact from builder stage
COPY --from=builder /build/target/orderflow-*.jar app.jar

USER orderflow:orderflow

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=30s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
