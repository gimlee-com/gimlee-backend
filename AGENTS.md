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
*   **Enums with Short Names:** For enums persisted in the database, use descriptive names (e.g., `GOOGLE_PRODUCT_TAXONOMY`) but provide a `shortName` property (e.g., `GPT`) for persistence. This keeps the code self-explanatory while maintaining a compact database representation. Implement a `fromShortName` lookup method in the enum's companion object for mapping back from the database.

### 2. Testing (`docs/development/testing-guidelines.md`)
*   **Framework:** Use **Kotest** for all tests.
*   **Priority:** Integration tests are the primary focus.
*   **Database:** Use **Testcontainers** with MongoDB for integration tests via `BaseIntegrationTest`.
*   **Test Isolation:** Since integration tests share the same MongoDB container (via `BaseIntegrationTest`), you **MUST** clear relevant collections in `beforeSpec` or `beforeTest` to ensure test independence.
*   **External API Mocking:** Use **WireMock** (available via `wireMockServer` in `BaseIntegrationTest`) to stub responses for all external service calls (e.g., exchange APIs, blockchain nodes) during integration tests.
*   **Test-Specific Configurations:** If an interface design offers flexibility that is not currently required in production code, do not implement unnecessary features in the production codebase just to satisfy integration tests. Instead, use `@TestConfiguration` within the test class to provide mock or stub beans to achieve a complete test context.
*   **Shared Test Fixtures:** Use the Gradle `java-test-fixtures` plugin (primarily in `gimlee-common`) to share common test utilities, base classes, and mocks across the project modules.
*   **Authentication Mocking:** When testing controllers that depend on `HttpServletRequestAuthUtil.getPrincipal()`, use `mockMvc` with `requestAttr("principal", principal)` to simulate an authenticated user.
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
*   **SOLID Principles:** Adhere strictly to SOLID principles. Specifically, ensure the **Dependency Inversion Principle** is followed by designing repositories to be generic and not tied to specific implementations and business logic.
*   **SRP:** Maintain strict separation between the modules and avoid circular dependencies.
*   **Self-Documenting Code:** Prefer extracting complex logic into well-named private methods over using inline comments. This makes the high-level flow of a method clear and self-explanatory.
*   **Events:** Use `ApplicationEventPublisher` and `gimlee-events` for loose coupling between modules.
*   **Roles:** Grant roles (e.g., `PIRATE`) dynamically based on user actions.
*   **DTO Intent:** Design creation DTOs with minimal fields to capture user intent early.
*   **Atomic Operations:** Use atomic MongoDB operations and aggregation pipelines for conditional updates to maintain data integrity without transactions.
*   **Domain Components:** Extract complex business logic into dedicated domain components (e.g., `AdStockService`) to maintain SRP and testability.
*   **Facade Controllers:** Use `gimlee-api` for facade controllers that coordinate multiple module services. This minimizes front-end requests.
*   **Decorator Pattern:** For complex initialization responses (e.g., `SessionInitController`), use a decorator pattern. Clients should be able to request specific data subsets via query parameters to optimize response payload and backend processing.
*   **Component Precedence:** When multiple implementations of an interface are used (e.g., multiple price providers), use Spring's `@Order` annotation to define their precedence (lowest value = highest priority). Ensure the consuming service implements a robust fallback mechanism if a high-priority provider fails.

### 5. Configuration (`docs/development/configuration-guidelines.md`)
*   **Externalize Everything:** Timeouts, prefixes, retention periods, and monitoring delays must be configurable via `application.yaml`.
*   **Documentation:** Maintain `application-local-EXAMPLE.yaml` with all available properties.

### 6. API Documentation (`docs/development/api-documentation-guidelines.md`)
For any module that exposes REST endpoints, we maintain `.http` files (IntelliJ HTTP Client format) and OpenAPI annotations to document and test the API.
*   **Supplement Controllers:** Every Controller must be supplemented with `.http` files that explore its full functionality with example requests.
*   **OpenAPI Annotations:** All controller methods must be documented with OpenAPI annotations (`@Operation`, `@ApiResponse`, `@Parameter`, etc.).
*   **Source of Truth:** The `.http` files serve as the source of truth for OpenAPI documentation. Descriptions and expected responses in annotations must match those in the `.http` files.
*   **Security & Roles:** Do not manually document security or roles in OpenAPI annotations. The `OpenApiConfig` automatically appends this information to the documentation based on the `@Privileged` annotation and path configurations.
*   **Error Documentation:** Common error responses (401 Unauthorized, 403 Forbidden) and the error response schema (`StatusResponseDto`) are automatically handled by the global `OperationCustomizer`.
*   **Stay in Sync:** Any addition or modification to Controllers requires corresponding updates to both their respective `.http` files and OpenAPI annotations to ensure consistency across all documentation formats.
*   **Comprehensive Error Data:** When reporting conflicts or errors (e.g., price mismatches), return the current state of all relevant items so the client can recover gracefully.
*   **Dynamic Response Documentation:** For controllers using dynamic response structures (e.g., Jackson's `@JsonAnyGetter`), create a dedicated "Documentation DTO". Use this DTO in the `@ApiResponse`'s `implementation` attribute to ensure the OpenAPI schema accurately reflects all possible decorator fields.

### 7. Docker & Infrastructure
*   **Module Synchronization:** This is a multi-module Gradle project where the `Dockerfile` explicitly copies each module's source directory to maintain a clean build context.
*   **Stay in Sync:** When adding a new module to the project, you **MUST** update the `Dockerfile` by adding a corresponding `COPY` command for that module before the build step.
*   **CI/CD Awareness:** Always verify that Docker builds succeed locally after project structure changes, as the GitHub Actions pipeline relies on the `Dockerfile` being perfectly in sync with the module list.

### 8. Standards & Internationalization
*   **Country Codes:** Always use **ISO 3166-1 alpha-2** codes (e.g., `US`, `PL`).
*   **Language Tags:** Strictly apply the **IETF standard** (ISO 639-1 language code combined with ISO 3166-1 alpha-2 country code, e.g., `en-US`, `pl-PL`).
*   **Validation:** Use `@IsoCountryCode` and `@IetfLanguageTag` annotations from `gimlee-common` for DTO validation.
*   **Module-Specific Bundles:** Each module maintains its own message bundles at `src/main/resources/i18n/{module}/messages.properties` to avoid classpath collisions.
*   **Basenames:** All module-specific bundles must be registered in the `spring.messages.basename` property in `application.yaml`.
*   **Currency Precision:** Always respect the `decimalPlaces` property defined in the `Currency` enum. When performing calculations or returning results, use `setScale(currency.decimalPlaces, RoundingMode.HALF_UP)` to ensure consistent precision across the system.
*   **Authoritative Timestamps:** When fetching data from external providers (e.g., exchange APIs), prioritize the authoritative timestamp provided by the source over the local fetch time to ensure data freshness is accurately represented in the system.

### 9. Unified API Status responses and Outcome System
*   **Outcome Interface:** Operations must return or report results using the `Outcome` interface from `gimlee-common`. Each module defines its own implementation (e.g., `AuthOutcome`, `AdOutcome`).
*   **Descriptive Slugs:** Use descriptive, machine-readable string slugs as codes (e.g., `AUTH_INCORRECT_CREDENTIALS`) instead of numeric status codes.
*   **Response Format:** Use `StatusResponseDto` for a unified response structure. It includes `success` (Boolean), `status` (String slug), `message` (Localized human-readable string), and optional `data` (Any).
*   **HTTP Status Mapping:** The `Outcome` determines the HTTP status code (via `httpCode`). Controllers must ensure the `ResponseEntity` status matches the outcome.
*   **I18n Integration:** Each `Outcome` provides a `messageKey` used to resolve localized messages via Spring's `MessageSource`.
*   **Exception Handling:** The `WebExceptionHandler` in `gimlee-api` automatically converts common security exceptions (`AuthenticationException`, `AuthorizationException`) into appropriate `Outcome`-based `StatusResponseDto` responses.

## Setup & Configuration
*   **Local Config:** Copy `gimlee-api/src/main/resources/application-local-EXAMPLE.yaml` to `application-local.yaml` and fill in details (PirateChain RPC, SMTP, etc.).
*   **Run Command:** `./gradlew :gimlee-api:bootRun --args='--spring.profiles.active=local'`
*   **API Docs:** Reference `.http` files in `docs/http/` directories within modules.
