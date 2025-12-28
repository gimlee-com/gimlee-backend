# Gimlee Project Context for AI Agents

## Project Overview
Gimlee is a decentralized, peer-to-peer cryptocurrency marketplace. It facilitates the exchange of goods and services using cryptocurrency payments (currently PirateChain/ARRR) without financial intermediaries. The system employs a non-custodial payment verification mechanism via viewing keys.

## Tech Stack & Architecture
*   **Language:** Kotlin (primary), Java 21
*   **Framework:** Spring Boot
*   **Build Tool:** Gradle (Multi-module)
*   **Database:** MongoDB 8.0+
*   **Infrastructure:** Docker, Terraform, Ansible

## Project Modules
*   `gimlee-api`: Main API application entry point.
*   `gimlee-ads`: Marketplace listings and advertisements.
*   `gimlee-auth`: Authentication, authorization, and user identity.
*   `gimlee-payments`: Blockchain integration (PirateChain) and payment verification.
*   `gimlee-media-store`: Media storage handling (Local Filesystem or S3).
*   `gimlee-notifications`: Email and notification services.
*   `gimlee-location`: Location-based services.
*   `gimlee-events`: Internal event definitions.
*   `gimlee-common`: Shared utilities and extensions.

## Critical Development Guidelines

### 1. MongoDB Design (`docs/development/mongodb-design-guidelines.md`)
*   **Plain Data Classes:** Model classes must be plain Kotlin data classes. **Do NOT** use Spring Data annotations (e.g., `@Document`, `@Id`, `@Indexed`).
*   **No Abstractions:** The use of Spring Data MongoDB abstractions (like `MongoRepository`) is **forbidden**.
*   **Migrations:** Indexes must be managed via Flyway migrations, never in code.
*   **Timestamps:** All timestamps must be stored as **epoch microseconds** (Long). Use `Instant.toMicros` extension from `gimlee-common`.
*   **Field Names:** Use abbreviations for all field names to minimize storage size.

### 2. Testing (`docs/development/testing-guidelines.md`)
*   **Framework:** Use **Kotest** for all tests.
*   **Priority:** Integration tests are the primary focus.
*   **Unit Tests:** Use sparingly, only for quirky internal logic. Keep them simple with minimal context/mocking.

### 3. Performance (`docs/development/performance-guidelines.md`)
*   **UUIDs:** Always use **UUIDv7** (via `com.gimlee.common.UUIDv7`) unless technically impossible due to specific index constraints.
*   **Resource Pooling:** All clients and thread pools must be pooled and fully configurable (size, timeouts) via application properties.
*   **Caching:**
    *   **Do NOT** use the `@Cacheable` annotation.
    *   Implement caching explicitly using **Caffeine** and **Kryo**.
    *   Each cache must be configured via properties to ensure resource visibility.

### 4. Architecture (`docs/development/architecture-guidelines.md`)
*   **SRP:** Maintain strict separation between business logic (`gimlee-orders`) and technical implementation (`gimlee-payments`).
*   **Events:** Use `ApplicationEventPublisher` and `gimlee-events` for loose coupling between modules.
*   **Roles:** Grant roles (e.g., `PIRATE`) dynamically based on user actions.

### 5. Configuration (`docs/development/configuration-guidelines.md`)
*   **Externalize Everything:** Timeouts, prefixes, and monitoring delays must be configurable via `application.yaml`.
*   **Documentation:** Maintain `application-local-EXAMPLE.yaml` with all available properties.

## Setup & Configuration
*   **Local Config:** Copy `gimlee-api/src/main/resources/application-local-EXAMPLE.yaml` to `application-local.yaml` and fill in details (PirateChain RPC, SMTP, etc.).
*   **Run Command:** `./gradlew :gimlee-api:bootRun --args='--spring.profiles.active=local'`
*   **API Docs:** Reference `.http` files in `docs/http/` directories within modules.
