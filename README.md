# InvoiceIQ

AI-Powered Invoice & Expense Automation Platform

## Architecture
Modular Monolith built with Java 21 and Spring Boot 3.x.

## Local Development Setup (Milestone 1: Foundation)

This milestone sets up the core foundation: Spring Boot, PostgreSQL, Redis, Flyway migrations, and basic Tenant/Auth domain entities.

### Prerequisites
- Docker & Docker Compose
- Java 21
- Maven

### Running the Infrastructure
Start the required databases and storage services using Docker Compose:

```bash
docker-compose up -d
```

This will start:
- PostgreSQL on port 5432
- Redis on port 6379

> **Note:** MinIO is temporarily disabled in `docker-compose.yml` because the image cannot currently be pulled anonymously. It will be enabled in a later milestone when invoice file storage is implemented.

### Running the Application
You can run the application using Maven with the `local` profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
*(If you are on Windows, use `mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local`)*

The application will start on `http://localhost:8080`.

### Health Check
Verify the application is running:
```bash
curl http://localhost:8080/actuator/health
```

### Running Tests
Tests use an in-memory H2 database with a `test` profile to ensure isolated, fast execution without requiring Docker locally.

```bash
./mvnw test
```
