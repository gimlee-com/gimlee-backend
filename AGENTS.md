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
*   `gimlee-purchases`: Purchase management.
*   `gimlee-user`: User preferences and user profile settings.
*   `gimlee-events`: Internal event definitions.
*   `gimlee-common`: Shared utilities and extensions.

## Critical Development Guidelines

### 1. MongoDB Design (`docs/development/mongodb-design-guidelines.md`)
*   **Plain Data Classes:** Model classes must be plain Kotlin data classes. **Do NOT** use Spring Data annotations (e.g., `@Document`, `@Id`, `@Indexed`).
*   **No Abstractions:** The use of Spring Data MongoDB abstractions (like `MongoRepository`) is **forbidden**.
*   **Migrations:** Indexes must be managed via Flyway migrations, never in code.
*   **Timestamps:** All timestamps must be stored as **epoch microseconds** (Long). Use `Instant.toMicros` extension from `gimlee-common`.
*   **Field Names:** Use abbreviations for all field names to minimize storage size.
*   **Index Optimization:** Only index fields used in queries. Use partial indexes for low-cardinality statuses.

### 2. Testing (`docs/development/testing-guidelines.md`)
*   **Framework:** Use **Kotest** for all tests.
*   **Priority:** Integration tests are the primary focus.
*   **Database:** Use **Testcontainers** with MongoDB for integration tests via `BaseIntegrationTest`.
*   **Unit Tests:** Use sparingly, only for quirky internal logic. Keep them simple with minimal context/mocking.

### 3. Performance (`docs/development/performance-guidelines.md`)
*   **UUIDs:** Always use **UUIDv7** (via `com.gimlee.common.UUIDv7`) unless technically impossible due to specific index constraints.
*   **Resource Pooling:** All clients and thread pools must be pooled and fully configurable (size, timeouts) via application properties.
*   **Caching:**
    *   **Do NOT** use the `@Cacheable` annotation.
    *   Implement caching explicitly using **Caffeine** and **Kryo**.
    *   Each cache must be configured via properties to ensure resource visibility.
*   **Thread Pools:** All thread pools must have the same core and maximum pool size to ensure consistent performance and avoid unexpected overhead. This should be configurable via a single application property.

### 4. Architecture (`docs/development/architecture-guidelines.md`)
*   **SRP:** Maintain strict separation between the modules and avoid circular dependencies.
*   **Self-Documenting Code:** Prefer extracting complex logic into well-named private methods over using inline comments. This makes the high-level flow of a method clear and self-explanatory.
*   **Events:** Use `ApplicationEventPublisher` and `gimlee-events` for loose coupling between modules.
*   **Roles:** Grant roles (e.g., `PIRATE`) dynamically based on user actions.
*   **DTO Intent:** Design creation DTOs with minimal fields to capture user intent early.

### 5. Configuration (`docs/development/configuration-guidelines.md`)
*   **Externalize Everything:** Timeouts, prefixes, and monitoring delays must be configurable via `application.yaml`.
*   **Documentation:** Maintain `application-local-EXAMPLE.yaml` with all available properties.

### 6. API Documentation (`docs/development/api-documentation-guidelines.md`)
For any module that exposes REST endpoints, we maintain `.http` files (IntelliJ HTTP Client format) and OpenAPI annotations to document and test the API.
*   **Supplement Controllers:** Every Controller must be supplemented with `.http` files that explore its full functionality with example requests.
*   **OpenAPI Annotations:** All controller methods must be documented with OpenAPI annotations (`@Operation`, `@ApiResponse`, `@Parameter`, etc.).
*   **Source of Truth:** The `.http` files serve as the source of truth for OpenAPI documentation. Descriptions and expected responses in annotations must match those in the `.http` files.
*   **Security & Roles:** Do not manually document security or roles in OpenAPI annotations. The `OpenApiConfig` automatically appends this information to the documentation based on the `@Privileged` annotation and path configurations.
*   **Stay in Sync:** Any addition or modification to Controllers requires corresponding updates to both their respective `.http` files and OpenAPI annotations to ensure consistency across all documentation formats.

### 7. Docker & Infrastructure
*   **Module Synchronization:** This is a multi-module Gradle project where the `Dockerfile` explicitly copies each module's source directory to maintain a clean build context.
*   **Stay in Sync:** When adding a new module to the project, you **MUST** update the `Dockerfile` by adding a corresponding `COPY` command for that module before the build step.
*   **CI/CD Awareness:** Always verify that Docker builds succeed locally after project structure changes, as the GitHub Actions pipeline relies on the `Dockerfile` being perfectly in sync with the module list.

### 8. Standards & Internationalization
*   **Country Codes:** Always use **ISO 3166-1 alpha-2** codes (e.g., `US`, `PL`).
*   **Language Tags:** Strictly apply the **IETF standard** (ISO 639-1 language code combined with ISO 3166-1 alpha-2 country code, e.g., `en-US`, `pl-PL`).
*   **Validation:** Use `@IsoCountryCode` and `@IetfLanguageTag` annotations from `gimlee-common` for DTO validation.

## Setup & Configuration
*   **Local Config:** Copy `gimlee-api/src/main/resources/application-local-EXAMPLE.yaml` to `application-local.yaml` and fill in details (PirateChain RPC, SMTP, etc.).
*   **Run Command:** `./gradlew :gimlee-api:bootRun --args='--spring.profiles.active=local'`
*   **API Docs:** Reference `.http` files in `docs/http/` directories within modules.
