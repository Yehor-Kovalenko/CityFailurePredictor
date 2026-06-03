FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY services/data-ingestion-service ./services/data-ingestion-service
RUN mvn -B clean package -f services/data-ingestion-service/pom.xml -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/services/data-ingestion-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]