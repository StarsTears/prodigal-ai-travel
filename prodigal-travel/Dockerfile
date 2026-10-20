FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy root pom and module pom first for better layer caching
COPY pom.xml ./
COPY prodigal-travel/pom.xml ./prodigal-travel/pom.xml
RUN mvn -pl prodigal-travel -am dependency:go-offline -B

# Copy source and build backend module
COPY prodigal-travel ./prodigal-travel
RUN mvn -pl prodigal-travel -am clean package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g @amap/amap-maps-mcp-server \
    && node -v \
    && npm -v \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/prodigal-travel/target/*.jar /app/app.jar

EXPOSE 8088

ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -sf -X POST http://127.0.0.1:8088/api/health/check | grep -q OK || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]