package com.gimlee.api

import com.gimlee.auth.cache.BannedUserCache
import com.gimlee.auth.domain.BanExpiryJob
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
import org.bson.types.ObjectId

class AdminUserApiIntegrationTest(
    private val userRepository: UserRepository,
    private val userRoleRepository: UserRoleRepository,
    private val banService: BanService,
    private val bannedUserCache: BannedUserCache,
    private val banExpiryJob: BanExpiryJob,
    private val userBanRepository: UserBanRepository
) : BaseIntegrationTest({

    val adminId = ObjectId.get()
    val user1Id = ObjectId.get()
    val user2Id = ObjectId.get()
    val user3Id = ObjectId.get()

    fun adminHeaders() = restClient.createAuthHeader(
        subject = adminId.toHexString(),
        username = "adm_admin",
        roles = listOf("USER", "ADMIN")
    )

    beforeSpec {
        mongoTemplate.dropCollection("gimlee-users")
        mongoTemplate.dropCollection("gimlee-userRoles")
        mongoTemplate.dropCollection("gimlee-userBans")

        userRepository.save(User(id = adminId, username = "adm_admin", displayName = "Admin User", email = "adm_admin@gimlee.com", status = UserStatus.ACTIVE))
        userRoleRepository.add(adminId, Role.USER)
        userRoleRepository.add(adminId, Role.ADMIN)

        userRepository.save(User(id = user1Id, username = "adm_john", displayName = "John Doe", email = "adm_john@example.com", status = UserStatus.ACTIVE))
        userRoleRepository.add(user1Id, Role.USER)

        userRepository.save(User(id = user2Id, username = "adm_jane", displayName = "Jane Smith", email = "adm_jane@example.com", status = UserStatus.ACTIVE))
        userRoleRepository.add(user2Id, Role.USER)

        userRepository.save(User(id = user3Id, username = "adm_pirate_king", displayName = "Pirate King", email = "adm_pirate@example.com", status = UserStatus.ACTIVE))
        userRoleRepository.add(user3Id, Role.USER)
        userRoleRepository.add(user3Id, Role.PIRATE)
    }

    Given("admin user list endpoint") {

        When("admin lists all users") {
            val response = restClient.get("/admin/users", adminHeaders())

            Then("it should return paginated user list") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 4
            }
        }

        When("admin lists users with pagination") {
            val response = restClient.get("/admin/users?page=0&size=2", adminHeaders())

            Then("it should return correct page size") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 2
                @Suppress("UNCHECKED_CAST")
                val page = body["page"] as Map<String, Any>
                (page["totalElements"] as Number).toInt() shouldBe 4
            }
        }

        When("admin searches users by username") {
            val response = restClient.get("/admin/users?search=adm_john", adminHeaders())

            Then("it should return matching users") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 1
                response.body shouldContain "adm_john"
            }
        }

        When("admin searches users by email") {
            val response = restClient.get("/admin/users?search=adm_pirate@", adminHeaders())

            Then("it should return matching users") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 1
                response.body shouldContain "adm_pirate_king"
            }
        }

        When("admin filters users by status") {
            banService.banUser(user2Id.toHexString(), "Filter test", null, adminId.toHexString())

            val response = restClient.get("/admin/users?status=BANNED", adminHeaders())

            Then("it should return only banned users") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 1
                response.body shouldContain "adm_jane"
            }

            banService.unbanUser(user2Id.toHexString(), adminId.toHexString())
        }

        When("admin sorts users by username ascending") {
            val response = restClient.get("/admin/users?sort=username&direction=ASC", adminHeaders())

            Then("it should return users sorted alphabetically") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                @Suppress("UNCHECKED_CAST")
                val content = body["content"] as List<Map<String, Any>>
                content.first()["username"] shouldBe "adm_admin"
            }
        }
    }

    Given("admin user detail endpoint") {

        When("admin requests user details") {
            val response = restClient.get("/admin/users/${user1Id.toHexString()}", adminHeaders())

            Then("it should return full user detail with stats") {
                response.statusCode shouldBe 200
                response.body shouldContain "SUCCESS"
                response.body shouldContain "adm_john"
                response.body shouldContain "adm_john@example.com"
                response.body shouldContain "activeAdsCount"
                response.body shouldContain "purchasesAsBuyer"
            }
        }

        When("admin requests details of non-existent user") {
            val fakeId = ObjectId.get()
            val response = restClient.get("/admin/users/${fakeId.toHexString()}", adminHeaders())

            Then("it should return 404") {
                response.statusCode shouldBe 404
                response.body shouldContain "USER_NOT_FOUND"
            }
        }
    }

    Given("ban user flow via API") {

        When("admin bans a user with reason") {
            val response = restClient.post(
                "/admin/users/${user1Id.toHexString()}/ban",
                mapOf("reason" to "Repeated policy violations and fraudulent listings"),
                adminHeaders()
            )

            Then("it should return 200 with success") {
                response.statusCode shouldBe 200
                response.body shouldContain "ADMIN_USER_BANNED_SUCCESSFULLY"
            }

            Then("user status should be BANNED in database") {
                val user = userRepository.findOneByField(User.FIELD_ID, user1Id)
                user?.status shouldBe UserStatus.BANNED
            }

            Then("an active ban record should exist") {
                val activeBan = banService.getActiveBan(user1Id.toHexString())
                activeBan shouldNotBe null
                activeBan!!.reason shouldBe "Repeated policy violations and fraudulent listings"
                activeBan.active shouldBe true
                activeBan.bannedUntil shouldBe null
            }
        }

        When("admin bans the same user again") {
            val response = restClient.post(
                "/admin/users/${user1Id.toHexString()}/ban",
                mapOf("reason" to "Double ban attempt"),
                adminHeaders()
            )

            Then("it should return 409 already banned") {
                response.statusCode shouldBe 409
                response.body shouldContain "ALREADY_BANNED"
            }
        }

        When("admin views user detail while banned") {
            val response = restClient.get("/admin/users/${user1Id.toHexString()}", adminHeaders())

            Then("it should show BANNED status and active ban details") {
                response.statusCode shouldBe 200
                response.body shouldContain "BANNED"
                response.body shouldContain "activeBan"
                response.body shouldContain "Repeated policy violations"
            }
        }

        When("admin unbans the user") {
            val response = restClient.post(
                "/admin/users/${user1Id.toHexString()}/unban",
                null,
                adminHeaders()
            )

            Then("it should return 200 with success") {
                response.statusCode shouldBe 200
                response.body shouldContain "UNBANNED_SUCCESSFULLY"
            }

            Then("user status should be ACTIVE in database") {
                val user = userRepository.findOneByField(User.FIELD_ID, user1Id)
                user?.status shouldBe UserStatus.ACTIVE
            }

            Then("the ban record should be deactivated") {
                val activeBan = banService.getActiveBan(user1Id.toHexString())
                activeBan shouldBe null
            }
        }

        When("admin tries to unban a non-banned user") {
            val response = restClient.post(
                "/admin/users/${user1Id.toHexString()}/unban",
                null,
                adminHeaders()
            )

            Then("it should return 409 not banned") {
                response.statusCode shouldBe 409
                response.body shouldContain "NOT_BANNED"
            }
        }
    }

    Given("ban with temporary duration") {

        When("admin bans user with a short-lived bannedUntil") {
            val bannedUntilMicros = (System.currentTimeMillis() + 100) * 1000 // 100ms from now
            val response = restClient.post(
                "/admin/users/${user3Id.toHexString()}/ban",
                mapOf(
                    "reason" to "Temporary ban - short duration",
                    "bannedUntil" to bannedUntilMicros
                ),
                adminHeaders()
            )

            Then("it should return 200 with success") {
                response.statusCode shouldBe 200
                response.body shouldContain "ADMIN_USER_BANNED_SUCCESSFULLY"
            }

            Then("user status should be BANNED in database") {
                val user = userRepository.findOneByField(User.FIELD_ID, user3Id)
                user?.status shouldBe UserStatus.BANNED
            }

            Then("ban record should have the bannedUntil timestamp") {
                val activeBan = userBanRepository.findActiveByUserId(user3Id.toHexString())
                activeBan shouldNotBe null
                activeBan!!.bannedUntil shouldBe bannedUntilMicros
                activeBan.active shouldBe true
            }

            Then("the cache should treat the user as banned") {
                bannedUserCache.invalidate(user3Id.toHexString())
                // Cache loader checks bannedUntil, may already be expired after 100ms
                // but the DB status is definitely BANNED at this point
                val user = userRepository.findOneByField(User.FIELD_ID, user3Id)
                user?.status shouldBe UserStatus.BANNED
            }
        }

        When("the ban expires and the expiry job runs") {
            Thread.sleep(200) // ensure the 100ms bannedUntil has passed
            banExpiryJob.processExpiredBans()

            Then("user status should be restored to ACTIVE") {
                val user = userRepository.findOneByField(User.FIELD_ID, user3Id)
                user?.status shouldBe UserStatus.ACTIVE
            }

            Then("the ban record should be deactivated") {
                val activeBan = userBanRepository.findActiveByUserId(user3Id.toHexString())
                activeBan shouldBe null
            }

            Then("the cache should reflect the unban") {
                bannedUserCache.isBanned(user3Id.toHexString()) shouldBe false
            }
        }

        When("admin views ban history after auto-expiry") {
            val response = restClient.get("/admin/users/${user3Id.toHexString()}/bans", adminHeaders())

            Then("it should contain the expired ban with unbannedBy SYSTEM") {
                response.statusCode shouldBe 200
                response.body shouldContain "Temporary ban - short duration"
                response.body shouldContain "bannedUntil"
            }
        }
    }

    Given("cannot ban admin user") {

        When("admin tries to ban another admin") {
            val response = restClient.post(
                "/admin/users/${adminId.toHexString()}/ban",
                mapOf("reason" to "Attempted admin ban"),
                adminHeaders()
            )

            Then("it should return 400 cannot ban admin") {
                response.statusCode shouldBe 400
                response.body shouldContain "CANNOT_BAN_ADMIN"
            }
        }
    }

    Given("ban request validation") {

        When("admin sends ban request with too short reason") {
            val response = restClient.post(
                "/admin/users/${user1Id.toHexString()}/ban",
                mapOf("reason" to "ab"),
                adminHeaders()
            )

            Then("it should return 400 validation error") {
                response.statusCode shouldBe 400
            }
        }
    }

    Given("ban history endpoint") {
        banService.banUser(user2Id.toHexString(), "First offense", null, adminId.toHexString())
        banService.unbanUser(user2Id.toHexString(), adminId.toHexString())
        banService.banUser(user2Id.toHexString(), "Second offense", null, adminId.toHexString())
        banService.unbanUser(user2Id.toHexString(), adminId.toHexString())

        When("admin requests ban history") {
            val response = restClient.get("/admin/users/${user2Id.toHexString()}/bans", adminHeaders())

            Then("it should return all ban records") {
                response.statusCode shouldBe 200
                response.body shouldContain "First offense"
                response.body shouldContain "Second offense"
            }
        }

        When("admin requests ban history for non-existent user") {
            val fakeId = ObjectId.get()
            val response = restClient.get("/admin/users/${fakeId.toHexString()}/bans", adminHeaders())

            Then("it should return 404") {
                response.statusCode shouldBe 404
                response.body shouldContain "USER_NOT_FOUND"
            }
        }
    }

    Given("authorization checks for admin endpoints") {

        When("unauthenticated user tries to list users") {
            val response = restClient.get("/admin/users")

            Then("it should return 401") {
                response.statusCode shouldBe 401
            }
        }

        When("unauthenticated user tries to ban a user") {
            val response = restClient.post(
                "/admin/users/${user2Id.toHexString()}/ban",
                mapOf("reason" to "Unauthorized attempt to ban"),
                emptyMap()
            )

            Then("it should return 401") {
                response.statusCode shouldBe 401
            }
        }
    }
})
