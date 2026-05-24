# Refresh Token Migration Guide — Frontend

## Overview

The authentication system has been upgraded from long-lived access tokens to a **short-lived access token + refresh token** model. This improves security by limiting exposure from stolen tokens and enabling server-side session revocation.

### What Changed

| Before | After |
|--------|-------|
| Login returns a single `accessToken` (14-day expiry) | Login returns `accessToken` (15 min) + `refreshToken` (30 days) |
| No way to revoke a token | Tokens can be revoked per-device or globally |
| No logout mechanism (stub) | Logout revokes the session server-side |
| No token renewal | Refresh endpoint issues new token pairs |

---

## API Changes

### 1. Login — `POST /api/auth/login`

**Request** (new field: `deviceId`):
```json
{
  "username": "john",
  "password": "secret123",
  "deviceId": "web-chrome-a1b2c3"
}
```

> `deviceId` is optional (defaults to `"unknown"`), but **required for security**. The server enforces device binding — refresh tokens can only be used from the same `deviceId` that was provided during login. Use a stable device fingerprint or a locally-generated UUID persisted in storage.

**Response** (new field: `refreshToken`):
```json
{
  "success": true,
  "status": "SUCCESS",
  "message": "Operation completed successfully.",
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
}
```

### 2. Verify User — `POST /api/auth/verifyUser`

**Request** (new field: `deviceId`):
```json
{
  "code": "123456",
  "deviceId": "web-chrome-a1b2c3"
}
```

**Response** — same shape as login (includes both `accessToken` and `refreshToken`).

### 3. Refresh Token — `POST /api/auth/token/refresh` *(NEW)*

> ⚠️ This endpoint does **NOT** require an `Authorization` header.

**Request**:
```json
{
  "refreshToken": "<current refresh token>",
  "deviceId": "web-chrome-a1b2c3"
}
```

**Success Response** (200):
```json
{
  "success": true,
  "status": "SUCCESS",
  "message": "Operation completed successfully.",
  "accessToken": "eyJ...<new access token>",
  "refreshToken": "bmV3...<new refresh token>"
}
```

**Error Responses** (401/429):

| Status Code | HTTP | Meaning | Action |
|-------------|------|---------|--------|
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | Token not recognized | Redirect to login |
| `AUTH_REFRESH_TOKEN_EXPIRED` | 401 | Token past 30-day TTL | Redirect to login |
| `AUTH_REFRESH_TOKEN_REUSE_DETECTED` | 401 | Security breach detected — all sessions in family revoked | Redirect to login, show security warning |
| `AUTH_REFRESH_TOKEN_DEVICE_MISMATCH` | 401 | Token used from wrong device | Redirect to login |
| *(no JSON body)* | 429 | Rate limited by load balancer (Traefik) | Retry after 60s backoff |

### 4. Logout — `POST /api/auth/logout` *(UPDATED)*

Now accepts a body to revoke the refresh token server-side.

**Request**:
```json
{
  "refreshToken": "<current refresh token>"
}
```

> The body is optional for backward compatibility, but **should always be sent** to properly invalidate the session.

**Response** (200):
```json
{
  "success": true,
  "status": "SUCCESS",
  "message": "Operation completed successfully."
}
```

### 5. Revoke All Sessions — `POST /api/auth/sessions/revoke-all` *(NEW)*

Logs the user out from **all devices**. Requires `Authorization` header.

**Request**: Empty body.

**Response** (200):
```json
{
  "success": true,
  "status": "SUCCESS",
  "message": "Operation completed successfully."
}
```

### 6. Revoke Specific Session — `POST /api/auth/sessions/revoke` *(NEW)*

Revokes a specific session by its refresh token. Requires `Authorization` header.

**Request**:
```json
{
  "refreshToken": "<token to revoke>"
}
```

---

## Frontend Implementation Guide

### Token Storage

| Token | Storage | Notes |
|-------|---------|-------|
| `accessToken` | In-memory (variable/state) | Never persisted to localStorage; lives only for the session |
| `refreshToken` | Secure persistent storage | `HttpOnly` cookie (ideal) or encrypted localStorage |
| `deviceId` | Persistent storage (localStorage) | Generate once, persist forever for this device |

### Token Lifecycle Flow

```
┌──────────────────────────────────────────────────────────────┐
│                        APP STARTUP                            │
├──────────────────────────────────────────────────────────────┤
│  1. Check if refreshToken exists in storage                  │
│  2. If yes → call POST /auth/token/refresh                   │
│  3. Store new accessToken in memory + new refreshToken        │
│  4. If refresh fails (401) → redirect to login               │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                     MAKING API REQUESTS                       │
├──────────────────────────────────────────────────────────────┤
│  1. Attach accessToken as: Authorization: Bearer <token>      │
│  2. If response is 401:                                       │
│     a. Call POST /auth/token/refresh                          │
│     b. If refresh succeeds → retry original request           │
│     c. If refresh fails → redirect to login                  │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    PROACTIVE REFRESH                          │
├──────────────────────────────────────────────────────────────┤
│  Option A: Decode JWT exp claim, refresh 1-2 min before       │
│  Option B: Refresh on every 401 (reactive)                    │
│                                                               │
│  Recommended: Option A (avoids failed request round-trip)     │
└──────────────────────────────────────────────────────────────┘
```

### Refresh Token Rotation — Critical Rules

1. **Always store the NEW refresh token** returned from `/auth/token/refresh`. The old one is immediately invalidated.
2. **Never retry with an old refresh token.** If a refresh call fails, do not retry — redirect to login.
3. **Handle `AUTH_REFRESH_TOKEN_REUSE_DETECTED`** specially: this means a potential token theft was detected. Show the user a security alert (e.g., "Your session was terminated for security reasons").

### Concurrency Handling

If multiple API calls fail simultaneously with 401, you must ensure **only one refresh request** is in-flight at a time. Recommended pattern:

```typescript
let refreshPromise: Promise<Tokens> | null = null;

async function getValidAccessToken(): Promise<string> {
  if (isTokenExpired(accessToken)) {
    if (!refreshPromise) {
      refreshPromise = refreshTokens().finally(() => { refreshPromise = null; });
    }
    const tokens = await refreshPromise;
    return tokens.accessToken;
  }
  return accessToken;
}
```

### Device ID Generation

Generate a stable device identifier on first launch and persist it:

```typescript
function getOrCreateDeviceId(): string {
  let deviceId = localStorage.getItem('gimlee_device_id');
  if (!deviceId) {
    deviceId = crypto.randomUUID();
    localStorage.setItem('gimlee_device_id', deviceId);
  }
  return deviceId;
}
```

### Logout Flow

```typescript
async function logout() {
  const refreshToken = getStoredRefreshToken();
  await api.post('/auth/logout', { refreshToken });
  clearAccessToken();
  clearRefreshToken();
  redirectToLogin();
}
```

---

## Token Timing Summary

| Token | TTL | Configurable |
|-------|-----|:---:|
| Access Token (JWT) | 15 minutes | ✅ `gimlee.auth.token.access-ttl-minutes` |
| Refresh Token | 30 days | ✅ `gimlee.auth.token.refresh-ttl-days` |

---

## Error Codes Reference

| Code | HTTP | When |
|------|------|------|
| `AUTH_INCORRECT_CREDENTIALS` | 200* | Wrong username/password |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | Token not found in system |
| `AUTH_REFRESH_TOKEN_EXPIRED` | 401 | Token past TTL |
| `AUTH_REFRESH_TOKEN_REUSE_DETECTED` | 401 | Previously-rotated token replayed (security event) |
| `AUTH_REFRESH_TOKEN_DEVICE_MISMATCH` | 401 | Refresh attempted from different deviceId than login |
| *(no JSON body — Traefik)* | 429 | Too many refresh attempts from this IP (max 10/min) |

*Login always returns 200; check `success` field.

---

## Security Behaviors

### Device Binding
The `deviceId` provided during login is permanently bound to the refresh token. Attempting to refresh from a different `deviceId` results in `AUTH_REFRESH_TOKEN_DEVICE_MISMATCH`. The client **must** send the same `deviceId` on refresh as was used during login.

### Family-Based Reuse Detection
All rotated refresh tokens share the same "family." If an attacker steals and uses a token that has already been rotated, the server detects this as reuse and **revokes the entire family** — both the attacker's and the legitimate user's active token. This forces re-authentication.

### Session Limits
Each user is limited to 10 concurrent sessions (configurable). When a new login exceeds this limit, the oldest session is automatically revoked. Users will need to re-authenticate on that device.

### Rate Limiting
The `/auth/token/refresh` endpoint is rate-limited at the load balancer (Traefik) to 10 requests per IP per minute with a burst allowance of 15. When rate limited, clients receive a standard HTTP `429 Too Many Requests` response directly from the reverse proxy (not from the application). Back off for at least 60 seconds before retrying.

### Known Limitation: Access Token Window
After revoking a session (via logout or revoke endpoints), the access token remains valid for up to its remaining TTL (max 15 minutes). For high-security operations, consider reducing `access-ttl-minutes` to 5. The server does not maintain an access token blacklist.

---

## Migration Checklist

- [ ] Update login request to include `deviceId`
- [ ] Store `refreshToken` from login/verify responses
- [ ] Implement token refresh logic (proactive or reactive)
- [ ] Handle concurrent 401s with single-flight refresh
- [ ] Update logout to send `refreshToken` in body
- [ ] Handle `AUTH_REFRESH_TOKEN_REUSE_DETECTED` with security alert
- [ ] Handle `AUTH_REFRESH_TOKEN_DEVICE_MISMATCH` (redirect to login)
- [ ] Handle HTTP 429 (rate limited by Traefik — no JSON body) with exponential backoff
- [ ] Generate and persist stable `deviceId` per device — **must be consistent across refreshes**
- [ ] (Optional) Add "Log out all devices" UI using `/auth/sessions/revoke-all`
