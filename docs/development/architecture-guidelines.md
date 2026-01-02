## Architecture Guidelines

### 1. Module Separation and SRP
*   **Single Responsibility Principle:** Each module must have a clear, distinct responsibility.
*   **Purchases vs. Payments:** 
    *   `gimlee-purchases` is responsible for business logic related to the purchase process, status transitions, and user interactions with purchases. It must NOT contain blockchain-specific details or low-level payment verification logic.
    *   `gimlee-payments` is responsible for technical payment processing, blockchain integration (e.g., Pirate Chain), transaction monitoring, and verifying if a payment meets specified criteria.
*   **Dependency Flow:** Higher-level business modules (like `gimlee-purchases`) may depend on lower-level technical modules (like `gimlee-payments`) for initialization, but coupling should be minimized.

### 2. Event-Driven Integration
*   **Loose Coupling:** Use internal JVM events (`ApplicationEventPublisher`) for cross-module communication to maintain loose coupling.
*   **Shared Events:** Define shared events in the `gimlee-events` module to avoid circular dependencies between functional modules.
*   **Asynchronous Processing:** When appropriate, use event listeners to trigger side effects in other modules (e.g., `PurchaseService` listening for `PaymentEvent` to update purchase status).

### 3. Role-Based Evolution
*   **Dynamic Role Granting:** Users may start with a base role (`USER`) and be granted additional roles (e.g., `PIRATE`) upon completing specific actions (like linking a cryptocurrency viewing key).
*   **Privileged Access:** Use the `@Privileged` annotation to enforce role-based access control at the controller level.

### 4. API and DTO Design
*   **Capture Intent Early:** Design creation DTOs with the absolute minimum required fields (e.g., just a `title` for an Ad). This allows the system to capture user intent even if they don't complete the full process immediately.
*   **Progressive Data Gathering:** Use separate update endpoints to gather detailed information after the initial entity creation.

### 5. Concurrency and Consistency
*   **Locked Stock Pattern:** For systems managing finite resources (like marketplace inventory), use a `lockedStock` attribute. 
    *   When a purchase is initiated, increment `lockedStock`.
    *   When a purchase is completed, decrement `lockedStock` and decrement total `stock`.
    *   When a purchase is canceled or times out, simply decrement `lockedStock`.
    *   `availableStock` is always calculated as `stock - lockedStock`.
*   **Atomic Operations:** Always use atomic MongoDB operations (`$inc`, `$set`, `$push`) in repositories to ensure thread-safe updates without requiring heavy application-level locking or transactions where possible.
