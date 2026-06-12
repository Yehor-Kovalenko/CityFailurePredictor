FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY services/decision-service ./services/decision-service
RUN mvn -B clean package -f services/decision-service/pom.xml -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/services/decision-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]