# Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests
# Version-independent artifact handoff: normalize the built jar to a stable
# name inside the BUILDER stage (where target/ exists) so pom.xml <version>
# bumps never break the image. The ".original" (pre-repackage) artifact is
# explicitly excluded.
RUN cp $(ls /app/target/*.jar | grep -v '\.original$' | head -n 1) /app/app.jar

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/app.jar /app/app.jar
# Run as non-root: the application must never run as root inside the container.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
