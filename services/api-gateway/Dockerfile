# ---------- BUILD STAGE ----------
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY . .
RUN mvn -B -pl services/api-gateway -am package -DskipTests

# ---------- RUNTIME STAGE ----------
FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /build/services/api-gateway/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]