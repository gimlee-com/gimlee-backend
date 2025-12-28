## Architecture Guidelines

### 1. Module Separation and SRP
*   **Single Responsibility Principle:** Each module must have a clear, distinct responsibility.
*   **Orders vs. Payments:** 
    *   `gimlee-orders` is responsible for business logic related to order management, status transitions, and user interactions with orders. It must NOT contain blockchain-specific details or low-level payment verification logic.
    *   `gimlee-payments` is responsible for technical payment processing, blockchain integration (e.g., Pirate Chain), transaction monitoring, and verifying if a payment meets specified criteria.
*   **Dependency Flow:** Higher-level business modules (like `gimlee-orders`) may depend on lower-level technical modules (like `gimlee-payments`) for initialization, but coupling should be minimized.

### 2. Event-Driven Integration
*   **Loose Coupling:** Use internal JVM events (`ApplicationEventPublisher`) for cross-module communication to maintain loose coupling.
*   **Shared Events:** Define shared events in the `gimlee-events` module to avoid circular dependencies between functional modules.
*   **Asynchronous Processing:** When appropriate, use event listeners to trigger side effects in other modules (e.g., `OrderService` listening for `PaymentEvent` to update order status).

### 3. Role-Based Evolution
*   **Dynamic Role Granting:** Users may start with a base role (`USER`) and be granted additional roles (e.g., `PIRATE`) upon completing specific actions (like linking a cryptocurrency viewing key).
*   **Privileged Access:** Use the `@Privileged` annotation to enforce role-based access control at the controller level.
