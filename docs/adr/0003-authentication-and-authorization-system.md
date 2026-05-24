# ADR 0003: Authentication and Authorization System

## Status
Accepted

## Context
Gimlee is a decentralized P2P cryptocurrency marketplace handling sensitive financial operations. The authentication and authorization system must:

1. Issue short-lived credentials to minimize exposure from token theft.
2. Provide server-side session revocation without requiring an access token blacklist.
3. Bind sessions to devices to prevent token portability.
4. Support role-based access control with hierarchical privileges.
5. Enforce user bans without breaking the authentication flow.
6. Scale horizontally without shared session state (stateless access tokens).
7. Avoid third-party auth libraries — the system must be fully self-contained.

## Decision

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           gimlee-auth                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  Registration ─→ Verification ─→ Login ─→ JWT + Refresh Token           │
│                                                                         │
│  Request Pipeline:                                                      │
│  Client → Traefik (rate limit) → JWTFilter → @Privileged → Controller  │
│                                                                         │
│  Post-Auth Enforcement:                                                 │
│  BannedUserInterceptor blocks mutating requests from banned users        │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1. Token Strategy: Short-Lived JWT + Opaque Refresh Token

| Token | Type | TTL | Storage (client) | Storage (server) |
|-------|------|-----|------------------|------------------|
| Access Token | JWT (HMAC256) | 15 min | In-memory only | None (stateless) |
| Refresh Token | Opaque (32 bytes, base64url) | 30 days | Persistent storage | SHA-256 hash in MongoDB |

**Design rationale**:
- Access tokens are stateless JWTs — the server never stores or looks them up. This enables horizontal scaling without shared state.
- Refresh tokens are opaque secrets stored as SHA-256 hashes. The server maintains full control over their lifecycle.
- The 15-minute access TTL limits the damage window if a token is stolen. The known trade-off is that revoked sessions retain access for up to 15 minutes (no blacklist).

### 2. Authentication Flow

#### Registration
```
POST /auth/register { username, email, password, displayName }
  → RegistrationService
    → Save user (status: ACTIVE, role: UNVERIFIED)
    → Generate 6-digit verification code
    → Send email with code (Mustache-rendered template)
    → Publish UserRegisteredEvent
    → Return access token (limited permissions)
```

#### Email Verification
```
POST /auth/verifyUser { code, deviceId }
  → UserVerificationService.verifyCode()
    → Validate code against VerificationCodeRepository
    → Remove UNVERIFIED role, add USER role
    → Set user status ACTIVE
    → Issue refresh token (bound to deviceId)
    → Return new access token + refresh token
```

#### Login
```
POST /auth/login { username, password, deviceId }
  → LoginService.login()
    → Load user by username
    → Block if status == SUSPENDED
    → Validate password (hex SHA hash + stored salt)
    → Issue JWT access token (JwtTokenService)
    → Issue refresh token (RefreshTokenService)
    → Update lastLogin timestamp
    → Return access + refresh tokens
```

#### Token Refresh (Rotation)
```
POST /auth/token/refresh { refreshToken, deviceId }
  → RefreshTokenService.rotateRefreshToken()
    → Lookup token by SHA-256 hash
    → If already revoked → REUSE DETECTED → revoke entire family
    → If expired → reject
    → If deviceId mismatch → reject
    → Revoke current token (mark family as revoked)
    → Issue new token in SAME family
    → Return new access + refresh tokens
```

#### Logout / Session Revocation
```
POST /auth/logout { refreshToken }           → Revoke token's family
POST /auth/sessions/revoke { refreshToken }  → Revoke specific session
POST /auth/sessions/revoke-all               → Revoke all user sessions
```

### 3. Refresh Token Security Model

#### Token Family Lineage
Every refresh token belongs to a **family** (UUID). On initial login, a new family is created. On each rotation, the new token inherits the same familyId. This enables:

- **Reuse detection**: If a revoked token is presented, the server knows the entire family is compromised and revokes all tokens in that lineage.
- **Cascading revocation**: An attacker who steals and uses a token triggers revocation of the legitimate user's session too, forcing re-authentication.

```
Login → familyId=A, token=T1
Refresh(T1) → familyId=A, token=T2 (T1 revoked)
Refresh(T2) → familyId=A, token=T3 (T2 revoked)

Attacker replays T1 → REUSE DETECTED → all tokens with familyId=A revoked
```

#### Device Binding
The `deviceId` provided at login is permanently bound to the refresh token. On rotation, the server:
- Rejects requests with a mismatched `deviceId`
- Preserves the original `deviceId` on the new token (ignores the request value)

This prevents stolen tokens from being used on a different device.

#### Session Limit
Each user is limited to a configurable maximum of concurrent sessions (default: 10). When a new login exceeds this limit, the oldest sessions are automatically revoked.

#### Cleanup Strategy
A scheduled job runs every 5 minutes (configurable) and processes tokens in bounded batches to avoid large database operations that could impact service performance during peak hours across global time zones:

1. **Expired tokens**: Delete up to `cleanup-batch-size` (default: 10,000) tokens past their 30-day TTL.
2. **Revoked tokens**: Delete up to `cleanup-batch-size` revoked tokens older than the retention window (7 days).

Each run logs the batch size at start and the number of records removed at completion, allowing operators to monitor whether the batch size is sufficient for the token accumulation rate.

### 4. Request Authentication Pipeline

#### JWTFilter (OncePerRequestFilter)
Registered via `FilterConfig` when `gimlee.auth.jwt.enabled=true`:

1. Extract `Authorization: Bearer <token>` header.
2. Verify JWT signature and issuer via `JwtTokenVerifier` (HMAC256).
3. Decode claims (subject, username, roles) into a `Principal` object.
4. Store `Principal` as a request-scoped attribute.
5. For unsecured paths (configured via `gimlee.auth.rest.unsecured-paths`): allow without JWT, set `Principal.EMPTY`.
6. For secured paths without valid JWT: reject with 401.

#### Principal Model
```kotlin
data class Principal(
    val userId: String,
    val username: String,
    val roles: List<Role>
) {
    companion object {
        val EMPTY = Principal("", "", emptyList())
    }
}
```

### 5. Authorization: Role Hierarchy

#### Roles
```kotlin
enum class Role {
    USER, ADMIN, SUPPORT, PUBLISHER, PIRATE, YCASH, UNVERIFIED
}
```

| Role | Hierarchy Level | Granted When |
|------|:-:|---|
| ADMIN | 100 | Manual assignment |
| SUPPORT | 80 | Manual assignment |
| PUBLISHER | 60 | User publishes first ad |
| PIRATE | 50 | User configures PirateChain wallet |
| YCASH | 50 | User configures YCash wallet |
| USER | 40 | Email verification complete |
| UNVERIFIED | 10 | Registration (before email verification) |

#### @Privileged Annotation
```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Privileged(val role: String = "")
```

#### AuthorizingAspect
An `@Around` AOP aspect intercepts all methods annotated with `@Privileged`:
- If `role` is specified: the user's highest role level must meet or exceed the required level.
- If `role` is empty: any authenticated user passes (authentication-only check).
- On failure: throws `AuthorizationException` (caught by `WebExceptionHandler` → 403).

Usage examples:
```kotlin
@Privileged(role = "USER")   // Requires verified user (level 40+)
@Privileged(role = "ADMIN")  // Requires admin (level 100)
@Privileged                   // Any authenticated user
```

#### UserActivityAspect
A secondary aspect on `@Privileged` that publishes `UserActivityEvent` on each authenticated request. Used for tracking user engagement and last-active timestamps.

### 6. Ban System

#### Design Philosophy
Banned users **can authenticate** (login succeeds). Restrictions are enforced post-authentication by `BannedUserInterceptor`. This ensures:
- Users can still read their data and understand why they are banned.
- The ban UX is handled at the application layer, not the auth layer.
- Temporary bans auto-expire without requiring re-registration.

Note: `SUSPENDED` status (distinct from `BANNED`) blocks login entirely.

#### BannedUserInterceptor
A Spring `HandlerInterceptor` that:
- Allows all requests with safe HTTP methods (GET, HEAD, OPTIONS).
- Allows endpoints annotated with `@AllowUserStatus(UserStatus.BANNED)`.
- Blocks all mutating requests (POST, PUT, DELETE, PATCH) from banned users with 403.

#### BannedUserCache (Caffeine)
- Key: userId → Value: Boolean (is banned)
- TTL: 300 seconds (configurable)
- Max size: 10,000 entries
- Invalidated immediately on ban/unban operations.

#### Ban Lifecycle
```
AdminController.banUser()
  → BanService.banUser()
    → Create UserBan record (userId, reason, bannedUntil, issuedBy)
    → Set user status = BANNED
    → Invalidate BannedUserCache
    → Publish UserBannedEvent

BanExpiryJob (scheduled)
  → Find expired bans (bannedUntil < now)
  → Deactivate ban, restore user status to ACTIVE
  → Invalidate cache
  → Publish UserUnbannedEvent
```

### 7. JWT Key Management

The JWT signing key is validated at application startup via `@PostConstruct`:
- Must not be blank.
- Must be at least 32 characters.
- Application fails fast if either condition is violated.

The key is injected via `gimlee.auth.rest.jwt.key` (environment variable in production: `GIMLEE_AUTH_REST_JWT_KEY`).

### 8. Rate Limiting (Infrastructure Layer)

Rate limiting for the unauthenticated `/auth/token/refresh` endpoint is handled at the **Traefik reverse proxy** level, not in application code:

```yaml
# Traefik middleware (deploy-app.yml)
refresh-rate-limit:
  average: 10        # requests per period
  period: 1m
  burst: 15          # maximum burst allowance
  sourcecriterion:
    ipstrategy:
      depth: 1       # use first X-Forwarded-For hop
```

**Rationale**: Application-level rate limiting (e.g., Caffeine cache) breaks under horizontal scaling — each instance maintains its own counter. Traefik provides centralized enforcement at the edge, and integrates with the existing CrowdSec bouncer for holistic abuse protection.

### 9. Data Model

#### RefreshToken (`gimlee-refreshTokens`)

| Domain Field | DB Field | Type | Description |
|---|---|---|---|
| id | _id | String (UUIDv7) | Primary key |
| userId | uid | String | Owner's user ID |
| familyId | fam | String (UUIDv7) | Token lineage group |
| hashedToken | tkn | String | SHA-256 hex of plaintext token |
| deviceId | dev | String | Bound device identifier |
| issuedAt | iat | Long | Epoch microseconds |
| expiresAt | exp | Long | Epoch microseconds |
| revoked | rev | Boolean | Whether token is revoked |
| revokedAt | rAt | Long? | Epoch microseconds when revoked |

#### Indexes (Flyway V003)
```javascript
// Lookup by hashed token (primary query path)
db.getCollection("gimlee-refreshTokens").createIndex(
  { tkn: 1 }, { unique: true }
);

// Revoke all tokens in a family
db.getCollection("gimlee-refreshTokens").createIndex(
  { fam: 1 }
);

// Find active sessions by user (for session limit enforcement)
db.getCollection("gimlee-refreshTokens").createIndex(
  { uid: 1, rev: 1, exp: 1 }
);

// TTL-based cleanup of expired tokens
db.getCollection("gimlee-refreshTokens").createIndex(
  { exp: 1 }
);
```

### 10. Configuration Reference

```yaml
gimlee:
  auth:
    jwt:
      enabled: true                          # Enable JWT filter
    token:
      access-ttl-minutes: 15                 # JWT access token lifetime
      refresh-ttl-days: 30                   # Refresh token lifetime
      cleanup-interval-ms: 300000            # Cleanup runs every 5 min (ms)
      cleanup-batch-size: 10000              # Max tokens removed per cleanup run
      max-sessions-per-user: 10              # Max concurrent sessions
      revoked-retention-days: 7              # Keep revoked tokens for reuse detection
    rest:
      jwt:
        issuer: gimlee                       # JWT issuer claim
        key: <env: GIMLEE_AUTH_REST_JWT_KEY>  # HMAC256 signing key (≥32 chars)
      unsecured-paths: >                     # Paths that don't require JWT
        /auth/login,
        /auth/register,
        /auth/register/**,
        /auth/token/refresh,
        ...
    ban:
      cache:
        ttl-seconds: 300                     # Ban cache TTL
        max-size: 10000                      # Ban cache max entries
      expiry-check-interval-ms: 60000        # Ban expiry scan interval
```

### 11. Security Considerations

| Threat | Mitigation |
|--------|-----------|
| Token theft (access) | 15-min TTL limits exposure window |
| Token theft (refresh) | Device binding prevents use on different device |
| Token replay | Family-based reuse detection revokes entire lineage |
| Brute force on refresh | Traefik rate limiting (10/min/IP) + CrowdSec |
| Weak signing key | Startup validation rejects keys < 32 chars |
| Unlimited sessions | Configurable cap (default 10) with oldest-first eviction |
| Bot abuse | CrowdSec scoring at Traefik edge |
| Banned user circumvention | Post-auth interceptor blocks writes; cache ensures fast checks |

### 12. Known Limitations

1. **Access token window**: After session revocation, the access token remains valid for up to 15 minutes. No server-side blacklist exists. This is an accepted trade-off for stateless scalability.
2. **No jti binding**: Access tokens have no link to the refresh token that spawned them. A revoked session's access token cannot be individually invalidated.
3. **No IP/geo tracking**: The system does not record or validate IP addresses across refresh operations. Future enhancement for a crypto marketplace.
4. **Single signing key**: All instances share one HMAC key. Key rotation requires coordinated deployment.

## Consequences

### Positive
- **Horizontally scalable**: Stateless JWTs require no shared session store for request authentication.
- **Self-contained**: No dependency on external auth providers (Keycloak, Auth0, etc.).
- **Defense in depth**: Multiple layers (device binding, reuse detection, rate limiting, ban system) provide overlapping protection.
- **Configurable**: All security parameters externalized to properties, allowing tuning per environment.
- **Graceful bans**: Banned users retain read access, improving UX for temporary bans.

### Negative
- **15-min revocation gap**: Compromised access tokens remain valid until expiry. Acceptable for current threat model.
- **Single point of failure for key**: If `GIMLEE_AUTH_REST_JWT_KEY` leaks, all tokens are forgeable. Requires infrastructure-level secret management.
- **No MFA**: Current system has no multi-factor authentication. Future consideration for high-value operations.
- **Memory overhead for ban cache**: Caffeine cache uses JVM heap. Bounded by `max-size` configuration.

### Future Extensibility

| Feature | Approach |
|---------|----------|
| MFA (TOTP) | Add verification step after password validation; gate refresh token issuance |
| OAuth2 providers | Add alternative login paths (Google, GitHub) alongside password auth |
| Access token blacklist | Redis-based set checked in JWTFilter; enables instant revocation |
| Key rotation | Dual-key verification period; new tokens signed with new key, old key accepted for TTL window |
| IP-based anomaly detection | Store IP on refresh; flag geographic impossibilities |
| Passkeys / WebAuthn | Add as alternative to password; bypass email verification |
