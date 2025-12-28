## Configuration Guidelines

### 1. Externalization of Parameters
*   **Flexibility:** All business-critical parameters and technical settings must be externalized to application properties. This allows for quick adjustments (e.g., changing timeouts or monitoring intervals) without code changes.
*   **Property Mapping:** Use `@ConfigurationProperties` to map groups of related properties to typed Kotlin data classes.
*   **Naming Convention:** Use a consistent prefix for all project-specific properties (e.g., `gimlee.payments.*`).

### 2. Configurable Features
*   **Timeouts and Deadlines:** Durations (like payment windows) should be configurable in hours or minutes.
*   **Constants as Properties:** Even seemingly static values like transaction memo prefixes should be configurable.
*   **Monitoring Intervals:** Polling delays and schedule intervals must be configurable, preferably supporting SpEL (Spring Expression Language) for `@Scheduled` annotations.

### 3. Documentation
*   **Application Examples:** Always update `gimlee-api/src/main/resources/application-local-EXAMPLE.yaml` when adding new properties to provide a reference for other developers.
