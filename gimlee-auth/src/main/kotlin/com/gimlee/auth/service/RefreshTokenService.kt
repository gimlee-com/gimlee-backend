package com.gimlee.auth.service

import com.gimlee.auth.domain.AuthOutcome
import com.gimlee.auth.domain.RefreshToken
import com.gimlee.auth.domain.auth.RefreshResult
import com.gimlee.auth.persistence.RefreshTokenRepository
import com.gimlee.common.UUIDv7
import com.gimlee.common.toMicros
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,

    @Value("\${gimlee.auth.token.refresh-ttl-days:30}")
    private val refreshTtlDays: Long,

    @Value("\${gimlee.auth.token.max-sessions-per-user:10}")
    private val maxSessionsPerUser: Int,

    @Value("\${gimlee.auth.token.revoked-retention-days:7}")
    private val revokedRetentionDays: Long,

    @Value("\${gimlee.auth.token.cleanup-batch-size:10000}")
    private val cleanupBatchSize: Int
) {
    companion object {
        private val log = LogManager.getLogger()
        private val secureRandom = SecureRandom()
        private const val TOKEN_BYTE_LENGTH = 32
    }

    data class IssuedRefreshToken(
        val plaintextToken: String,
        val familyId: java.util.UUID
    )

    fun issueRefreshToken(userId: String, deviceId: String): IssuedRefreshToken {
        val plaintextToken = generateOpaqueToken()
        val hashedToken = hashToken(plaintextToken)
        val now = Instant.now()
        val familyId = UUIDv7.generate()

        val token = RefreshToken(
            id = UUIDv7.generate(),
            userId = userId,
            familyId = familyId,
            hashedToken = hashedToken,
            deviceId = deviceId,
            issuedAt = now.toMicros(),
            expiresAt = now.plus(refreshTtlDays, ChronoUnit.DAYS).toMicros()
        )

        refreshTokenRepository.save(token)
        enforceSessionLimit(userId)
        return IssuedRefreshToken(plaintextToken, familyId)
    }

    fun rotateRefreshToken(plaintextToken: String, deviceId: String): RefreshResult {
        val hashedToken = hashToken(plaintextToken)
        val existing = refreshTokenRepository.findByHashedToken(hashedToken)
            ?: return RefreshResult.Failure(AuthOutcome.REFRESH_TOKEN_INVALID)

        if (existing.revoked) {
            // Reuse detected — revoke the entire token family
            log.warn("Refresh token reuse detected for family=${existing.familyId}, user=${existing.userId}. Revoking family.")
            refreshTokenRepository.revokeByFamily(existing.familyId)
            return RefreshResult.Failure(AuthOutcome.REFRESH_TOKEN_REUSE_DETECTED)
        }

        val now = Instant.now().toMicros()
        if (existing.expiresAt < now) {
            return RefreshResult.Failure(AuthOutcome.REFRESH_TOKEN_EXPIRED)
        }

        // Enforce device binding — reject if deviceId doesn't match
        if (existing.deviceId != deviceId) {
            log.warn("Refresh token device mismatch for family=${existing.familyId}, user=${existing.userId}. Expected=${existing.deviceId}, got=$deviceId")
            return RefreshResult.Failure(AuthOutcome.REFRESH_TOKEN_DEVICE_MISMATCH)
        }

        // Revoke the current token (rotation)
        refreshTokenRepository.revokeByFamily(existing.familyId)

        // Issue a new token in the SAME family — preserves lineage for reuse detection
        val newPlaintextToken = generateOpaqueToken()
        val newHashedToken = hashToken(newPlaintextToken)
        val nowInstant = Instant.now()

        val newToken = RefreshToken(
            id = UUIDv7.generate(),
            userId = existing.userId,
            familyId = existing.familyId,
            hashedToken = newHashedToken,
            deviceId = existing.deviceId,
            issuedAt = nowInstant.toMicros(),
            expiresAt = nowInstant.plus(refreshTtlDays, ChronoUnit.DAYS).toMicros()
        )

        refreshTokenRepository.save(newToken)
        return RefreshResult.Success(
            userId = existing.userId,
            newPlaintextToken = newPlaintextToken
        )
    }

    fun revokeSession(plaintextToken: String, userId: String): Boolean {
        val hashedToken = hashToken(plaintextToken)
        val existing = refreshTokenRepository.findByHashedToken(hashedToken)
            ?: return false

        if (existing.userId != userId) return false

        refreshTokenRepository.revokeByFamily(existing.familyId)
        return true
    }

    fun revokeAllSessions(userId: String) {
        refreshTokenRepository.revokeAllForUser(userId)
    }

    @Scheduled(fixedRateString = "\${gimlee.auth.token.cleanup-interval-ms:300000}")
    fun cleanupExpired() {
        log.info("Refresh token cleanup started (batch size: $cleanupBatchSize)")

        val expiredCutoff = Instant.now().toMicros()
        val deletedExpired = refreshTokenRepository.deleteExpired(expiredCutoff, cleanupBatchSize)

        val revokedCutoff = Instant.now().minus(revokedRetentionDays, ChronoUnit.DAYS).toMicros()
        val deletedRevoked = refreshTokenRepository.deleteRevokedBefore(revokedCutoff, cleanupBatchSize)

        log.info("Refresh token cleanup completed: $deletedExpired expired, $deletedRevoked revoked removed")
    }

    private fun enforceSessionLimit(userId: String) {
        val activeSessions = refreshTokenRepository.findActiveSessionsByUser(userId)
        if (activeSessions.size > maxSessionsPerUser) {
            val sessionsToRevoke = activeSessions
                .sortedBy { it.issuedAt }
                .take(activeSessions.size - maxSessionsPerUser)
            sessionsToRevoke.forEach { session ->
                refreshTokenRepository.revokeByFamily(session.familyId)
            }
            log.info("Revoked ${sessionsToRevoke.size} oldest sessions for user=$userId (limit=$maxSessionsPerUser)")
        }
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(token.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
