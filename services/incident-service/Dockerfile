FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY services/incident-service ./services/incident-service
RUN mvn -B clean package -f services/incident-service/pom.xml -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/services/incident-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]