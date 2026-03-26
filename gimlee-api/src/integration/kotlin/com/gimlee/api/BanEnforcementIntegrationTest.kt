package com.gimlee.api

import com.gimlee.auth.cache.BannedUserCache
import com.gimlee.auth.domain.BanService
import com.gimlee.auth.domain.User
import com.gimlee.auth.domain.UserStatus
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserBanRepository
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.bson.types.ObjectId

class BanEnforcementIntegrationTest(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val banService: BanService,
    private val bannedUserCache: BannedUserCache,
    private val userBanRepository: UserBanRepository
) : BaseIntegrationTest({

    val adminUserId = ObjectId.get()
    val bannedUserId = ObjectId.get()
    val activeUserId = ObjectId.get()

    fun adminHeaders() = restClient.createAuthHeader(
        subject = adminUserId.toHexString(),
        username = "ban_admin",
        roles = listOf("USER", "ADMIN")
    )

    fun bannedUserHeaders() = restClient.createAuthHeader(
        subject = bannedUserId.toHexString(),
        username = "ban_banneduser",
        roles = listOf("USER")
    )

    fun activeUserHeaders() = restClient.createAuthHeader(
        subject = activeUserId.toHexString(),
        username = "ban_activeuser",
        roles = listOf("USER")
    )

    beforeSpec {
        mongoTemplate.dropCollection("gimlee-users")
        mongoTemplate.dropCollection("gimlee-userRoles")
        mongoTemplate.dropCollection("gimlee-userBans")

        userRepository.save(User(id = adminUserId, username = "ban_admin", displayName = "Admin", email = "ban_admin@gimlee.com", status = UserStatus.ACTIVE))
        userRoleRepository.add(adminUserId, Role.USER)
        userRoleRepository.add(adminUserId, Role.ADMIN)

        userRepository.save(User(id = bannedUserId, username = "ban_banneduser", displayName = "Banned", email = "ban_banned@gimlee.com", status = UserStatus.ACTIVE))
        userRoleRepository.add(bannedUserId, Role.USER)

        userRepository.save(User(id = activeUserId, username = "ban_activeuser", displayName = "Active", email = "ban_active@gimlee.com", status = UserStatus.ACTIVE))
        userRoleRepository.add(activeUserId, Role.USER)
    }

    Given("a banned user attempting mutating requests") {
        banService.banUser(bannedUserId.toHexString(), "Test policy violation", null, adminUserId.toHexString())

        When("the banned user attempts a POST request") {
            val response = restClient.post(
                "/auth/logout",
                null,
                bannedUserHeaders().plus("X-Ban-Test" to "post-blocked")
            )

            // Note: logout is whitelisted via @AllowUserStatus, so we test another POST
            val blockedResponse = restClient.post(
                "/sales/ads",
                mapOf("title" to "Should be blocked"),
                bannedUserHeaders()
            )

            Then("it should be blocked with 403 USER_BANNED") {
                blockedResponse.statusCode shouldBe 403
                blockedResponse.body shouldContain "USER_BANNED"
            }
        }

        When("the banned user attempts a GET request (read-only)") {
            val response = restClient.get("/ads/categories", bannedUserHeaders())

            Then("it should be allowed") {
                response.statusCode shouldBe 200
            }
        }

        When("the banned user attempts to logout (whitelisted endpoint)") {
            val response = restClient.post("/auth/logout", null, bannedUserHeaders())

            Then("it should be allowed since logout is whitelisted") {
                response.statusCode shouldBe 200
            }
        }

        // Clean up for next test block
        banService.unbanUser(bannedUserId.toHexString(), adminUserId.toHexString())
    }

    Given("an active user is not affected by ban interceptor") {

        When("an active user attempts a POST request") {
            val response = restClient.post("/auth/logout", null, activeUserHeaders())

            Then("it should not be blocked by ban interceptor") {
                val body = response.body ?: ""
                body shouldNotContain "USER_BANNED"
            }
        }
    }

    Given("unban restores access for a banned user") {
        banService.banUser(bannedUserId.toHexString(), "Restore access test", null, adminUserId.toHexString())

        When("admin unbans the user") {
            banService.unbanUser(bannedUserId.toHexString(), adminUserId.toHexString())

            Then("the user should no longer be banned in cache") {
                bannedUserCache.isBanned(bannedUserId.toHexString()) shouldBe false
            }

            Then("the user status should be ACTIVE in database") {
                val user = userRepository.findOneByField(User.FIELD_ID, bannedUserId)
                user?.status shouldBe UserStatus.ACTIVE
            }
        }

        When("the unbanned user attempts a POST request") {
            val response = restClient.post("/auth/logout", null, bannedUserHeaders())

            Then("it should not be blocked") {
                val body = response.body ?: ""
                body shouldNotContain "USER_BANNED"
            }
        }
    }

    Given("cache invalidation works correctly on ban and unban") {

        When("admin bans a user") {
            banService.banUser(bannedUserId.toHexString(), "Cache test", null, adminUserId.toHexString())

            Then("the cache should immediately reflect the ban") {
                bannedUserCache.isBanned(bannedUserId.toHexString()) shouldBe true
            }
        }

        When("admin unbans the user") {
            banService.unbanUser(bannedUserId.toHexString(), adminUserId.toHexString())

            Then("the cache should immediately reflect the unban") {
                bannedUserCache.isBanned(bannedUserId.toHexString()) shouldBe false
            }
        }
    }

    Given("temporary ban with expired bannedUntil is treated as not banned") {
        val pastMicros = (System.currentTimeMillis() - 60_000) * 1000 // 1 minute ago

        When("admin bans user with a bannedUntil timestamp in the past") {
            banService.banUser(bannedUserId.toHexString(), "Short temp ban", pastMicros, adminUserId.toHexString())

            Then("the cache should treat the user as not banned (expired)") {
                bannedUserCache.invalidate(bannedUserId.toHexString())
                bannedUserCache.isBanned(bannedUserId.toHexString()) shouldBe false
            }

            // Clean up
            banService.unbanUser(bannedUserId.toHexString(), adminUserId.toHexString())
        }
    }

    Given("unauthenticated requests are not affected by ban interceptor") {

        When("an unauthenticated GET request is made to an unsecured endpoint") {
            val response = restClient.get("/ads/categories")

            Then("it should pass through without ban check") {
                response.statusCode shouldBe 200
            }
        }
    }
})
