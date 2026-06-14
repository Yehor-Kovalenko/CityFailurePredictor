FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY services/map-service ./services/map-service
RUN mvn -B clean package -f services/map-service/pom.xml -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/services/map-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]