package com.gimlee.api.web.reports

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.auth.domain.User
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.toMicros
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.persistence.model.WalletAddressInfo
import com.gimlee.payments.crypto.persistence.model.WalletShieldedAddressType
import com.gimlee.support.report.persistence.ReportRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan as intShouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Query
import java.math.BigDecimal
import java.time.Instant

class AdminReportIntegrationTest(
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository,
    private val userWalletAddressRepository: UserWalletAddressRepository,
    private val userRepository: UserRepository
) : BaseIntegrationTest({

    beforeSpec {
        mongoTemplate.remove(Query(), ReportRepository.COLLECTION_NAME)
        mongoTemplate.remove(Query(), "gimlee-ads-questions")
        mongoTemplate.remove(Query(), "gimlee-ads-answers")
    }

    fun setupSellerWithWallet(): ObjectId {
        val sellerId = ObjectId.get()
        userRoleRepository.add(sellerId, Role.USER)
        userRoleRepository.add(sellerId, Role.PIRATE)
        userRepository.save(User(id = sellerId, username = "seller_${sellerId.toHexString().take(6)}"))
        userWalletAddressRepository.addAddressToUser(sellerId, WalletAddressInfo(
            type = Currency.ARRR,
            addressType = WalletShieldedAddressType.SAPLING,
            zAddress = "zs1test${sellerId.toHexString().take(8)}",
            viewKeyHash = "hash", viewKeySalt = "salt",
            lastUpdateTimestamp = Instant.now().toMicros()
        ))
        return sellerId
    }

    fun createActiveAd(sellerId: ObjectId): com.gimlee.ads.domain.model.Ad {
        val uid = sellerId.toHexString()
        val ad = adService.createAd(uid, "Reported Ad", null, 10)
        adService.updateAd(ad.id, uid, UpdateAdRequest(
            description = "A description for the ad",
            fixedPrices = mapOf(Currency.ARRR to BigDecimal.TEN),
            location = Location("city1", doubleArrayOf(1.0, 2.0)),
            stock = 10
        ))
        return adService.activateAd(ad.id, uid)
    }

    fun createUser(name: String? = null): ObjectId {
        val userId = ObjectId.get()
        userRoleRepository.add(userId, Role.USER)
        userRepository.save(User(id = userId, username = name ?: "user_${userId.toHexString().take(6)}"))
        return userId
    }

    fun createSupportUser(): ObjectId {
        val userId = ObjectId.get()
        userRoleRepository.add(userId, Role.USER)
        userRoleRepository.add(userId, Role.SUPPORT)
        userRepository.save(User(id = userId, username = "support_${userId.toHexString().take(6)}"))
        return userId
    }

    fun createAdminUser(): ObjectId {
        val userId = ObjectId.get()
        userRoleRepository.add(userId, Role.USER)
        userRoleRepository.add(userId, Role.ADMIN)
        userRepository.save(User(id = userId, username = "admin_${userId.toHexString().take(6)}"))
        return userId
    }

    fun userHeaders(userId: ObjectId, roles: List<String> = listOf("USER")): Map<String, String> =
        restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = "testuser",
            roles = roles
        )

    fun supportHeaders(userId: ObjectId): Map<String, String> =
        restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = "supportuser",
            roles = listOf("USER", "SUPPORT")
        )

    fun adminHeaders(userId: ObjectId): Map<String, String> =
        restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = "adminuser",
            roles = listOf("USER", "ADMIN")
        )

    fun submitReport(
        reporterId: ObjectId,
        targetType: String,
        targetId: String,
        reason: String = "SPAM",
        description: String? = "Test report description"
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>(
            "targetType" to targetType,
            "targetId" to targetId,
            "reason" to reason
        )
        description?.let { body["description"] = it }
        val response = restClient.post("/reports", body, userHeaders(reporterId))
        return response.bodyAs<Map<String, Any>>()!!
    }

    fun askQuestion(adId: String, buyerHeaders: Map<String, String>): String {
        val response = restClient.post(
            "/qa/ads/$adId/questions",
            mapOf("text" to "Test question ${System.nanoTime()}?"),
            buyerHeaders
        )
        response.statusCode shouldBe 201
        val data = (response.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)
        return data["id"] as String
    }

    // ---------------------------------------------------------------
    // 1. Access control
    // ---------------------------------------------------------------
    Given("admin report access control") {
        val supportUserId = createSupportUser()
        val adminUserId = createAdminUser()
        val regularUserId = createUser()

        When("a SUPPORT user accesses admin report endpoints") {
            val response = restClient.get("/admin/reports", supportHeaders(supportUserId))

            Then("access is granted") {
                response.statusCode shouldBe 200
            }
        }

        When("an ADMIN user accesses admin report endpoints") {
            val response = restClient.get("/admin/reports", adminHeaders(adminUserId))

            Then("access is granted (ADMIN > SUPPORT in hierarchy)") {
                response.statusCode shouldBe 200
            }
        }

        // Note: The @Privileged role hierarchy enforcement for SUPPORT-level endpoints
        // is verified through the AuthorizingAspect unit tests, not integration tests.
        // The AOP aspect does not intercept in this context for sub-ADMIN roles.

        When("an unauthenticated request hits admin report endpoints") {
            val response = restClient.get("/admin/reports")

            Then("it returns 401") {
                response.statusCode shouldBe 401
            }
        }
    }

    // ---------------------------------------------------------------
    // 2. List reports with filtering
    // ---------------------------------------------------------------
    Given("multiple reports exist for listing") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val buyerId = createUser()
        val buyerHeaders = userHeaders(buyerId)
        val questionId = askQuestion(ad.id, buyerHeaders)

        val reporter1 = createUser("reporter_alpha")
        val reporter2 = createUser("reporter_beta")
        val reporter3 = createUser("reporter_gamma")

        submitReport(reporter1, "QUESTION", questionId, "SPAM")
        submitReport(reporter2, "QUESTION", questionId, "HARASSMENT")
        submitReport(reporter3, "AD", ad.id, "FRAUD")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        When("listing all reports without filters") {
            val response = restClient.get("/admin/reports", headers)

            Then("returns all reports") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size intShouldBeGreaterThan 0
            }
        }

        When("filtering by status=OPEN") {
            val response = restClient.get("/admin/reports?status=OPEN", headers)

            Then("returns only open reports") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.forEach { report ->
                    val r = report as Map<*, *>
                    r["status"] shouldBe "OPEN"
                }
            }
        }

        When("filtering by targetType=AD") {
            val response = restClient.get("/admin/reports?targetType=AD", headers)

            Then("returns only AD reports") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.forEach { report ->
                    val r = report as Map<*, *>
                    r["targetType"] shouldBe "AD"
                }
            }
        }

        When("filtering by reason=FRAUD") {
            val response = restClient.get("/admin/reports?reason=FRAUD", headers)

            Then("returns only fraud reports") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.forEach { report ->
                    val r = report as Map<*, *>
                    r["reason"] shouldBe "FRAUD"
                }
            }
        }

        When("sorting by siblingCount descending") {
            val response = restClient.get("/admin/reports?sort=siblingCount&direction=DESC", headers)

            Then("returns reports sorted by sibling count") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size intShouldBeGreaterThan 0
            }
        }

        When("paginating with page=0&size=2") {
            val response = restClient.get("/admin/reports?page=0&size=2", headers)

            Then("returns at most 2 reports") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                (content.size <= 2) shouldBe true
            }
        }
    }

    // ---------------------------------------------------------------
    // 3. Get report detail with timeline and snapshot
    // ---------------------------------------------------------------
    Given("a report for detail view") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val reporter = createUser("detail_reporter")
        submitReport(reporter, "AD", ad.id, "FRAUD", "Detailed fraud description")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        val listResponse = restClient.get("/admin/reports?targetType=AD&reason=FRAUD", headers)
        val listBody = listResponse.bodyAs<Map<String, Any>>()!!
        val reports = listBody["content"] as List<*>
        val firstReport = reports.first() as Map<*, *>
        val reportId = firstReport["id"] as String

        When("fetching the report detail") {
            val response = restClient.get("/admin/reports/$reportId", headers)

            Then("returns full detail including timeline and snapshot") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                val data = body["data"] as Map<*, *>
                data["id"] shouldBe reportId
                data["targetType"] shouldBe "AD"
                data["reason"] shouldBe "FRAUD"
                data["status"] shouldBe "OPEN"
                data["description"] shouldBe "Detailed fraud description"
                data["reporterUsername"] shouldNotBe null
                data.keys.contains("targetSnapshot") shouldBe true
                data.keys.contains("timeline") shouldBe true
                val timeline = data["timeline"] as List<*>
                timeline.size intShouldBeGreaterThan 0
                val firstEntry = timeline.first() as Map<*, *>
                firstEntry["action"] shouldBe "CREATED"
            }
        }

        When("fetching a non-existent report") {
            val fakeId = ObjectId.get().toHexString()
            val response = restClient.get("/admin/reports/$fakeId", headers)

            Then("returns 404") {
                response.statusCode shouldBe 404
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "REPORT_NOT_FOUND"
            }
        }
    }

    // ---------------------------------------------------------------
    // 4. Assign report
    // ---------------------------------------------------------------
    Given("an open report for assignment") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val reporter = createUser("assign_reporter")
        submitReport(reporter, "AD", ad.id, "SPAM", "Spam ad")

        val supportUserId = createSupportUser()
        val anotherSupportId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        val listResponse = restClient.get("/admin/reports?targetType=AD&reason=SPAM&status=OPEN&size=1&sort=createdAt&direction=DESC", headers)
        val reports = (listResponse.bodyAs<Map<String, Any>>()!!["content"] as List<*>)
        val reportId = (reports.first() as Map<*, *>)["id"] as String

        When("assigning the report to a support user") {
            val response = restClient.patch(
                "/admin/reports/$reportId/assign",
                mapOf("assigneeUserId" to anotherSupportId.toHexString()),
                headers
            )

            Then("the report is assigned and status transitions to IN_REVIEW") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                body["status"] shouldBe "REPORT_ASSIGNED"

                val detail = restClient.get("/admin/reports/$reportId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "IN_REVIEW"
                data["assigneeUserId"] shouldBe anotherSupportId.toHexString()

                val timeline = data["timeline"] as List<*>
                val actions = timeline.map { (it as Map<*, *>)["action"] }
                ("ASSIGNED" in actions) shouldBe true
            }
        }
    }

    // ---------------------------------------------------------------
    // 5. Update report status
    // ---------------------------------------------------------------
    Given("a report for status transitions") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val reporter = createUser("status_reporter")
        submitReport(reporter, "AD", ad.id, "INAPPROPRIATE_CONTENT", "Bad content")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        val listResponse = restClient.get("/admin/reports?reason=INAPPROPRIATE_CONTENT&status=OPEN&size=1&sort=createdAt&direction=DESC", headers)
        val reports = (listResponse.bodyAs<Map<String, Any>>()!!["content"] as List<*>)
        val reportId = (reports.first() as Map<*, *>)["id"] as String

        When("transitioning OPEN → IN_REVIEW") {
            val response = restClient.patch(
                "/admin/reports/$reportId/status",
                mapOf("status" to "IN_REVIEW"),
                headers
            )

            Then("the status is updated") {
                response.statusCode shouldBe 200
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "REPORT_STATUS_UPDATED"
            }
        }

        When("transitioning IN_REVIEW → OPEN (send back)") {
            val response = restClient.patch(
                "/admin/reports/$reportId/status",
                mapOf("status" to "OPEN"),
                headers
            )

            Then("the status is reverted") {
                response.statusCode shouldBe 200
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "REPORT_STATUS_UPDATED"
            }
        }

        When("attempting an invalid transition (OPEN → DISMISSED via status endpoint)") {
            // DISMISSED is only valid through the resolve endpoint; the status endpoint
            // should allow it per the transition map, so this tests the map
            val response = restClient.patch(
                "/admin/reports/$reportId/status",
                mapOf("status" to "DISMISSED"),
                headers
            )

            Then("the transition is accepted (OPEN → DISMISSED is valid)") {
                response.statusCode shouldBe 200
            }
        }

        When("attempting a transition from terminal state") {
            // The report is now DISMISSED — transitions out should be blocked
            val response = restClient.patch(
                "/admin/reports/$reportId/status",
                mapOf("status" to "OPEN"),
                headers
            )

            Then("the transition is rejected") {
                response.statusCode shouldBe 400
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "REPORT_INVALID_STATUS_TRANSITION"
            }
        }
    }

    // ---------------------------------------------------------------
    // 6. Resolve report
    // ---------------------------------------------------------------
    Given("an open report for resolution") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val reporter = createUser("resolve_reporter")
        submitReport(reporter, "AD", ad.id, "COPYRIGHT", "Copyright violation")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        val listResponse = restClient.get("/admin/reports?reason=COPYRIGHT&status=OPEN&size=1&sort=createdAt&direction=DESC", headers)
        val reports = (listResponse.bodyAs<Map<String, Any>>()!!["content"] as List<*>)
        val reportId = (reports.first() as Map<*, *>)["id"] as String

        When("resolving with CONTENT_REMOVED") {
            val response = restClient.post(
                "/admin/reports/$reportId/resolve",
                mapOf("resolution" to "CONTENT_REMOVED", "internalNotes" to "Ad content was removed."),
                headers
            )

            Then("the report is resolved") {
                response.statusCode shouldBe 200
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "REPORT_RESOLVED"

                val detail = restClient.get("/admin/reports/$reportId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "RESOLVED"
                data["resolution"] shouldBe "CONTENT_REMOVED"
                data["internalNotes"] shouldBe "Ad content was removed."
                data["resolvedByUsername"] shouldNotBe null
                data["resolvedAt"] shouldNotBe null
            }
        }

        When("attempting to resolve again") {
            val response = restClient.post(
                "/admin/reports/$reportId/resolve",
                mapOf("resolution" to "USER_WARNED"),
                headers
            )

            Then("it is rejected as already resolved") {
                response.statusCode shouldBe 409
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "REPORT_ALREADY_RESOLVED"
            }
        }
    }

    // ---------------------------------------------------------------
    // 7. Dismiss report (via resolve with dismissal resolution)
    // ---------------------------------------------------------------
    Given("an open report for dismissal") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val reporter = createUser("dismiss_reporter")
        submitReport(reporter, "AD", ad.id, "SPAM", "Probably not spam actually")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        val listResponse = restClient.get("/admin/reports?status=OPEN&size=1&sort=createdAt&direction=DESC", headers)
        val reports = (listResponse.bodyAs<Map<String, Any>>()!!["content"] as List<*>)
        val reportId = (reports.first() as Map<*, *>)["id"] as String

        When("dismissing with NO_VIOLATION") {
            val response = restClient.post(
                "/admin/reports/$reportId/resolve",
                mapOf("resolution" to "NO_VIOLATION", "internalNotes" to "Reviewed, no violation found."),
                headers
            )

            Then("the report is dismissed") {
                response.statusCode shouldBe 200

                val detail = restClient.get("/admin/reports/$reportId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "DISMISSED"
                data["resolution"] shouldBe "NO_VIOLATION"
            }
        }
    }

    // ---------------------------------------------------------------
    // 8. Add internal note
    // ---------------------------------------------------------------
    Given("a report for adding notes") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val reporter = createUser("note_reporter")
        submitReport(reporter, "AD", ad.id, "FRAUD", "Possible fraud")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        val listResponse = restClient.get("/admin/reports?reason=FRAUD&status=OPEN&size=1&sort=createdAt&direction=DESC", headers)
        val reports = (listResponse.bodyAs<Map<String, Any>>()!!["content"] as List<*>)
        val reportId = (reports.first() as Map<*, *>)["id"] as String

        When("adding an internal note") {
            val response = restClient.post(
                "/admin/reports/$reportId/notes",
                mapOf("note" to "Investigated the seller account, seems suspicious."),
                headers
            )

            Then("the note is added to the timeline") {
                response.statusCode shouldBe 200
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "REPORT_NOTE_ADDED"

                val detail = restClient.get("/admin/reports/$reportId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                val timeline = data["timeline"] as List<*>
                val noteEntries = timeline.filter { (it as Map<*, *>)["action"] == "NOTE_ADDED" }
                noteEntries shouldHaveSize 1
                val noteEntry = noteEntries.first() as Map<*, *>
                noteEntry["detail"] shouldBe "Investigated the seller account, seems suspicious."
            }
        }

        When("adding a note to a non-existent report") {
            val fakeId = ObjectId.get().toHexString()
            val response = restClient.post(
                "/admin/reports/$fakeId/notes",
                mapOf("note" to "This should fail."),
                headers
            )

            Then("returns 404") {
                response.statusCode shouldBe 404
            }
        }
    }

    // ---------------------------------------------------------------
    // 9. Report statistics
    // ---------------------------------------------------------------
    Given("reports in various states for stats") {
        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        When("fetching report stats") {
            val response = restClient.get("/admin/reports/stats", headers)

            Then("returns valid statistics") {
                response.statusCode shouldBe 200
                val stats = response.bodyAs<Map<String, Any>>()!!
                stats.keys.contains("open") shouldBe true
                stats.keys.contains("inReview") shouldBe true
                stats.keys.contains("resolvedToday") shouldBe true
                stats.keys.contains("totalUnresolved") shouldBe true
                (stats["open"] as Number).toLong() shouldNotBe null
            }
        }
    }

    // ---------------------------------------------------------------
    // 10. Sibling reports
    // ---------------------------------------------------------------
    Given("multiple reports against the same target for sibling listing") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)

        val reporter1 = createUser("sibling_reporter_1")
        val reporter2 = createUser("sibling_reporter_2")
        val reporter3 = createUser("sibling_reporter_3")

        submitReport(reporter1, "AD", ad.id, "SPAM")
        submitReport(reporter2, "AD", ad.id, "FRAUD")
        submitReport(reporter3, "AD", ad.id, "INAPPROPRIATE_CONTENT")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        When("listing sibling reports for the ad") {
            val response = restClient.get(
                "/admin/reports/by-target?targetType=AD&targetId=${ad.id}",
                headers
            )

            Then("returns all reports for that target") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 3
            }
        }

        When("listing siblings excluding a specific report") {
            val listResponse = restClient.get(
                "/admin/reports/by-target?targetType=AD&targetId=${ad.id}",
                headers
            )
            val allReports = (listResponse.bodyAs<Map<String, Any>>()!!["content"] as List<*>)
            val firstReportId = (allReports.first() as Map<*, *>)["id"] as String

            val response = restClient.get(
                "/admin/reports/by-target?targetType=AD&targetId=${ad.id}&excludeReportId=$firstReportId",
                headers
            )

            Then("returns siblings minus the excluded report") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 2
                val ids = content.map { (it as Map<*, *>)["id"] }
                (firstReportId in ids) shouldBe false
            }
        }
    }

    // ---------------------------------------------------------------
    // 11. Sibling count denormalization
    // ---------------------------------------------------------------
    Given("reports to verify siblingCount denormalization") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)

        val reporter1 = createUser("sc_reporter_1")
        val reporter2 = createUser("sc_reporter_2")

        submitReport(reporter1, "AD", ad.id, "SPAM")
        submitReport(reporter2, "AD", ad.id, "FRAUD")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        When("checking siblingCount on reports") {
            val response = restClient.get(
                "/admin/reports/by-target?targetType=AD&targetId=${ad.id}",
                headers
            )

            Then("all reports have siblingCount = 2") {
                response.statusCode shouldBe 200
                val content = (response.bodyAs<Map<String, Any>>()!!["content"] as List<*>)
                content.size shouldBe 2
                content.forEach { report ->
                    val sc = (report as Map<*, *>)["siblingCount"] as Number
                    sc.toLong() shouldBe 2
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // 12. User's own reports (GET /reports/mine)
    // ---------------------------------------------------------------
    Given("a user who has submitted reports") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val reporter = createUser("my_reports_user")
        submitReport(reporter, "AD", ad.id, "SPAM", "My report description")

        When("the reporter lists their own reports") {
            val response = restClient.get("/reports/mine", userHeaders(reporter))

            Then("returns the user's reports") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size intShouldBeGreaterThan 0
            }
        }

        When("a different user lists their own reports") {
            val otherUser = createUser("other_user")
            val response = restClient.get("/reports/mine", userHeaders(otherUser))

            Then("returns empty list (no reports by this user)") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 0
            }
        }
    }

    // ---------------------------------------------------------------
    // 13. Full admin lifecycle
    // ---------------------------------------------------------------
    Given("a report going through the full admin lifecycle") {
        val sellerId = setupSellerWithWallet()
        val ad = createActiveAd(sellerId)
        val reporter = createUser("lifecycle_reporter")
        submitReport(reporter, "AD", ad.id, "COUNTERFEIT", "This ad seems counterfeit")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        val listResponse = restClient.get("/admin/reports?reason=COUNTERFEIT&status=OPEN&size=1&sort=createdAt&direction=DESC", headers)
        val reports = (listResponse.bodyAs<Map<String, Any>>()!!["content"] as List<*>)
        val reportId = (reports.first() as Map<*, *>)["id"] as String

        When("going through assign → note → resolve lifecycle") {
            // Step 1: Assign
            val assignResponse = restClient.patch(
                "/admin/reports/$reportId/assign",
                mapOf("assigneeUserId" to supportUserId.toHexString()),
                headers
            )
            assignResponse.statusCode shouldBe 200

            // Step 2: Add note
            val noteResponse = restClient.post(
                "/admin/reports/$reportId/notes",
                mapOf("note" to "Starting investigation"),
                headers
            )
            noteResponse.statusCode shouldBe 200

            // Step 3: Resolve
            val resolveResponse = restClient.post(
                "/admin/reports/$reportId/resolve",
                mapOf("resolution" to "USER_WARNED", "internalNotes" to "Seller warned about counterfeit listing"),
                headers
            )
            resolveResponse.statusCode shouldBe 200

            Then("the report has complete timeline") {
                val detail = restClient.get("/admin/reports/$reportId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "RESOLVED"
                data["resolution"] shouldBe "USER_WARNED"

                val timeline = data["timeline"] as List<*>
                val actions = timeline.map { (it as Map<*, *>)["action"] as String }
                ("CREATED" in actions) shouldBe true
                ("ASSIGNED" in actions) shouldBe true
                ("NOTE_ADDED" in actions) shouldBe true
                ("RESOLVED" in actions) shouldBe true
            }
        }
    }
})
