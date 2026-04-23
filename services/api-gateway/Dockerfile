FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY services ./services
RUN mvn -B clean package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/services/api-gateway/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]