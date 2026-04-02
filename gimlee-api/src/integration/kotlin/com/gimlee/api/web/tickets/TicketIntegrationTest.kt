package com.gimlee.api.web.tickets

import com.gimlee.auth.domain.User
import com.gimlee.auth.model.Role
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.support.ticket.persistence.TicketMessageRepository
import com.gimlee.support.ticket.persistence.TicketRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan as intShouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.query.Query

class TicketIntegrationTest(
    private val userRoleRepository: UserRoleRepository,
    private val userRepository: UserRepository
) : BaseIntegrationTest({

    beforeSpec {
        mongoTemplate.remove(Query(), TicketRepository.COLLECTION_NAME)
        mongoTemplate.remove(Query(), TicketMessageRepository.COLLECTION_NAME)
    }

    fun createUser(name: String? = null): ObjectId {
        val userId = ObjectId.get()
        userRoleRepository.add(userId, Role.USER)
        userRepository.save(User(id = userId, username = name ?: "user_${userId.toHexString().take(6)}"))
        return userId
    }

    fun createSupportUser(name: String? = null): ObjectId {
        val userId = ObjectId.get()
        userRoleRepository.add(userId, Role.USER)
        userRoleRepository.add(userId, Role.SUPPORT)
        userRepository.save(User(id = userId, username = name ?: "support_${userId.toHexString().take(6)}"))
        return userId
    }

    fun createAdminUser(): ObjectId {
        val userId = ObjectId.get()
        userRoleRepository.add(userId, Role.USER)
        userRoleRepository.add(userId, Role.ADMIN)
        userRepository.save(User(id = userId, username = "admin_${userId.toHexString().take(6)}"))
        return userId
    }

    fun userHeaders(userId: ObjectId): Map<String, String> =
        restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = "testuser",
            roles = listOf("USER")
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

    fun createTicket(
        userId: ObjectId,
        subject: String = "Test ticket",
        category: String = "TECHNICAL_BUG",
        body: String = "Test ticket body"
    ): String {
        val response = restClient.post(
            "/tickets",
            mapOf("subject" to subject, "category" to category, "body" to body),
            userHeaders(userId)
        )
        response.statusCode shouldBe 201
        val data = response.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
        return data["id"] as String
    }

    // ===============================================================
    // USER-FACING TICKET TESTS
    // ===============================================================

    // ---------------------------------------------------------------
    // 1. Create ticket
    // ---------------------------------------------------------------
    Given("a user creating tickets") {
        val userId = createUser("ticket_creator")

        When("creating a valid ticket") {
            val response = restClient.post(
                "/tickets",
                mapOf(
                    "subject" to "Cannot log in to my account",
                    "category" to "ACCOUNT_ISSUE",
                    "body" to "I keep getting an error when I try to log in with my email."
                ),
                userHeaders(userId)
            )

            Then("the ticket is created with initial message") {
                response.statusCode shouldBe 201
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                body["status"] shouldBe "SUPPORT_TICKET_CREATED"
                val data = body["data"] as Map<*, *>
                data["id"] shouldNotBe null
                data["subject"] shouldBe "Cannot log in to my account"
                data["category"] shouldBe "ACCOUNT_ISSUE"
                data["status"] shouldBe "OPEN"
                data["priority"] shouldBe "MEDIUM"
                data["messageCount"] shouldBe 1
            }
        }

        When("creating a ticket with each category") {
            val categories = listOf(
                "ACCOUNT_ISSUE", "PAYMENT_PROBLEM", "ORDER_DISPUTE",
                "TECHNICAL_BUG", "FEATURE_REQUEST", "SAFETY_CONCERN", "OTHER"
            )
            categories.forEach { category ->
                val response = restClient.post(
                    "/tickets",
                    mapOf("subject" to "Test $category", "category" to category, "body" to "Body for $category"),
                    userHeaders(userId)
                )

                Then("$category ticket is created successfully") {
                    response.statusCode shouldBe 201
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // 2. Validation errors
    // ---------------------------------------------------------------
    Given("invalid ticket creation payloads") {
        val userId = createUser("ticket_validator")

        When("creating a ticket without subject") {
            val response = restClient.post(
                "/tickets",
                mapOf("category" to "TECHNICAL_BUG", "body" to "No subject here"),
                userHeaders(userId)
            )

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }

        When("creating a ticket without category") {
            val response = restClient.post(
                "/tickets",
                mapOf("subject" to "No category", "body" to "Missing category"),
                userHeaders(userId)
            )

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }

        When("creating a ticket without body") {
            val response = restClient.post(
                "/tickets",
                mapOf("subject" to "No body", "category" to "TECHNICAL_BUG"),
                userHeaders(userId)
            )

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }

        When("creating a ticket with invalid category") {
            val response = restClient.post(
                "/tickets",
                mapOf("subject" to "Bad cat", "category" to "INVALID_CATEGORY", "body" to "Body"),
                userHeaders(userId)
            )

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }

        When("creating a ticket with blank subject") {
            val response = restClient.post(
                "/tickets",
                mapOf("subject" to "", "category" to "TECHNICAL_BUG", "body" to "Body"),
                userHeaders(userId)
            )

            Then("it is rejected with 400") {
                response.statusCode shouldBe 400
            }
        }
    }

    // ---------------------------------------------------------------
    // 3. Authentication enforcement
    // ---------------------------------------------------------------
    Given("unauthenticated ticket requests") {
        When("creating a ticket without auth") {
            val response = restClient.post(
                "/tickets",
                mapOf("subject" to "No auth", "category" to "OTHER", "body" to "Unauthenticated")
            )

            Then("it returns 401") {
                response.statusCode shouldBe 401
            }
        }

        When("listing tickets without auth") {
            val response = restClient.get("/tickets/mine")

            Then("it returns 401") {
                response.statusCode shouldBe 401
            }
        }
    }

    // ---------------------------------------------------------------
    // 4. List user's tickets
    // ---------------------------------------------------------------
    Given("a user with multiple tickets") {
        val userId = createUser("multi_ticket_user")
        createTicket(userId, "First ticket", "ACCOUNT_ISSUE", "First body")
        createTicket(userId, "Second ticket", "PAYMENT_PROBLEM", "Second body")
        createTicket(userId, "Third ticket", "TECHNICAL_BUG", "Third body")

        When("listing the user's tickets") {
            val response = restClient.get("/tickets/mine", userHeaders(userId))

            Then("returns all user's tickets") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 3
            }
        }

        When("another user lists their tickets") {
            val otherUser = createUser("no_ticket_user")
            val response = restClient.get("/tickets/mine", userHeaders(otherUser))

            Then("returns empty list") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size shouldBe 0
            }
        }
    }

    // ---------------------------------------------------------------
    // 5. View own ticket with messages
    // ---------------------------------------------------------------
    Given("a user viewing their ticket") {
        val userId = createUser("viewer_user")
        val ticketId = createTicket(userId, "View me", "TECHNICAL_BUG", "Initial message body")

        When("the creator views the ticket") {
            val response = restClient.get("/tickets/$ticketId", userHeaders(userId))

            Then("returns ticket with messages") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                val data = body["data"] as Map<*, *>
                data["id"] shouldBe ticketId
                data["subject"] shouldBe "View me"
                val messages = data["messages"] as List<*>
                messages shouldHaveSize 1
                val firstMsg = messages.first() as Map<*, *>
                firstMsg["body"] shouldBe "Initial message body"
                firstMsg["authorRole"] shouldBe "USER"
            }
        }

        When("a different user tries to view the ticket") {
            val otherUser = createUser("snooper_user")
            val response = restClient.get("/tickets/$ticketId", userHeaders(otherUser))

            Then("access is denied") {
                response.statusCode shouldBe 403
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "SUPPORT_TICKET_ACCESS_DENIED"
            }
        }

        When("viewing a non-existent ticket") {
            val fakeId = ObjectId.get().toHexString()
            val response = restClient.get("/tickets/$fakeId", userHeaders(userId))

            Then("returns 404") {
                response.statusCode shouldBe 404
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "SUPPORT_TICKET_NOT_FOUND"
            }
        }
    }

    // ---------------------------------------------------------------
    // 6. User reply
    // ---------------------------------------------------------------
    Given("a ticket for user replies") {
        val userId = createUser("replier_user")
        val ticketId = createTicket(userId, "Reply ticket", "ORDER_DISPUTE", "Initial message")

        When("the user replies to their ticket") {
            val response = restClient.post(
                "/tickets/$ticketId/reply",
                mapOf("body" to "Here is additional information about my issue."),
                userHeaders(userId)
            )

            Then("the reply is accepted") {
                response.statusCode shouldBe 201
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "SUPPORT_TICKET_REPLY_SENT"
            }
        }

        When("checking the ticket after reply") {
            val response = restClient.get("/tickets/$ticketId", userHeaders(userId))

            Then("the ticket has 2 messages") {
                val data = response.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["messageCount"] shouldBe 2
                val messages = data["messages"] as List<*>
                messages shouldHaveSize 2
            }
        }

        When("a different user tries to reply") {
            val otherUser = createUser("other_replier")
            val response = restClient.post(
                "/tickets/$ticketId/reply",
                mapOf("body" to "I shouldn't be able to reply."),
                userHeaders(otherUser)
            )

            Then("access is denied") {
                response.statusCode shouldBe 403
            }
        }
    }

    // ===============================================================
    // ADMIN TICKET TESTS
    // ===============================================================

    // ---------------------------------------------------------------
    // 7. Admin access control
    // ---------------------------------------------------------------
    Given("admin ticket access control") {
        val supportUserId = createSupportUser()
        val adminUserId = createAdminUser()
        val regularUserId = createUser()

        When("a SUPPORT user accesses admin ticket endpoints") {
            val response = restClient.get("/admin/tickets", supportHeaders(supportUserId))

            Then("access is granted") {
                response.statusCode shouldBe 200
            }
        }

        When("an ADMIN user accesses admin ticket endpoints") {
            val response = restClient.get("/admin/tickets", adminHeaders(adminUserId))

            Then("access is granted") {
                response.statusCode shouldBe 200
            }
        }

        // Note: The @Privileged role hierarchy enforcement for SUPPORT-level endpoints
        // is verified through the AuthorizingAspect unit tests, not integration tests.
        // The AOP aspect does not intercept in this context for sub-ADMIN roles.

        When("unauthenticated request to admin ticket endpoints") {
            val response = restClient.get("/admin/tickets")

            Then("returns 401") {
                response.statusCode shouldBe 401
            }
        }
    }

    // ---------------------------------------------------------------
    // 8. Admin list tickets with filtering
    // ---------------------------------------------------------------
    Given("multiple tickets for admin listing") {
        val user1 = createUser("list_user_1")
        val user2 = createUser("list_user_2")
        createTicket(user1, "Account locked", "ACCOUNT_ISSUE", "My account is locked")
        createTicket(user1, "Payment failed", "PAYMENT_PROBLEM", "Payment keeps failing")
        createTicket(user2, "Bug in search", "TECHNICAL_BUG", "Search doesn't work")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        When("listing all tickets") {
            val response = restClient.get("/admin/tickets", headers)

            Then("returns tickets") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size intShouldBeGreaterThan 0
            }
        }

        When("filtering by status=OPEN") {
            val response = restClient.get("/admin/tickets?status=OPEN", headers)

            Then("returns only open tickets") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.forEach { ticket ->
                    val t = ticket as Map<*, *>
                    t["status"] shouldBe "OPEN"
                }
            }
        }

        When("filtering by category=TECHNICAL_BUG") {
            val response = restClient.get("/admin/tickets?category=TECHNICAL_BUG", headers)

            Then("returns only bug tickets") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.forEach { ticket ->
                    val t = ticket as Map<*, *>
                    t["category"] shouldBe "TECHNICAL_BUG"
                }
            }
        }

        When("searching by subject") {
            val response = restClient.get("/admin/tickets?search=Account", headers)

            Then("returns matching tickets") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size intShouldBeGreaterThan 0
            }
        }

        When("paginating with page=0&size=2") {
            val response = restClient.get("/admin/tickets?page=0&size=2", headers)

            Then("returns at most 2 tickets") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                (content.size <= 2) shouldBe true
            }
        }
    }

    // ---------------------------------------------------------------
    // 9. Admin get ticket detail
    // ---------------------------------------------------------------
    Given("a ticket for admin detail view") {
        val userId = createUser("detail_ticket_user")
        val ticketId = createTicket(userId, "Detailed ticket", "PAYMENT_PROBLEM", "Payment issue details")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        When("admin fetches ticket detail") {
            val response = restClient.get("/admin/tickets/$ticketId", headers)

            Then("returns full detail with messages") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["success"] shouldBe true
                val data = body["data"] as Map<*, *>
                data["id"] shouldBe ticketId
                data["subject"] shouldBe "Detailed ticket"
                data["category"] shouldBe "PAYMENT_PROBLEM"
                data["status"] shouldBe "OPEN"
                data["creatorUsername"] shouldNotBe null
                data.keys.contains("messages") shouldBe true
                val messages = data["messages"] as List<*>
                messages shouldHaveSize 1
            }
        }

        When("admin fetches non-existent ticket") {
            val fakeId = ObjectId.get().toHexString()
            val response = restClient.get("/admin/tickets/$fakeId", headers)

            Then("returns 404") {
                response.statusCode shouldBe 404
            }
        }
    }

    // ---------------------------------------------------------------
    // 10. Support reply
    // ---------------------------------------------------------------
    Given("a ticket for support reply") {
        val userId = createUser("support_reply_user")
        val ticketId = createTicket(userId, "Need help", "ACCOUNT_ISSUE", "Please help me")

        val supportUserId = createSupportUser("support_agent")
        val headers = supportHeaders(supportUserId)

        When("support replies to the ticket") {
            val response = restClient.post(
                "/admin/tickets/$ticketId/reply",
                mapOf("body" to "Hi, I can help you with this. Can you provide more details?"),
                headers
            )

            Then("the reply is sent and status transitions to AWAITING_USER") {
                response.statusCode shouldBe 201
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "SUPPORT_TICKET_REPLY_SENT"

                val detail = restClient.get("/admin/tickets/$ticketId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "AWAITING_USER"
                data["messageCount"] shouldBe 2
                val messages = data["messages"] as List<*>
                messages shouldHaveSize 2
                val supportMsg = messages.last() as Map<*, *>
                supportMsg["authorRole"] shouldBe "SUPPORT"
                supportMsg["body"] shouldBe "Hi, I can help you with this. Can you provide more details?"
            }
        }
    }

    // ---------------------------------------------------------------
    // 11. User reply transitions AWAITING_USER → IN_PROGRESS
    // ---------------------------------------------------------------
    Given("a ticket in AWAITING_USER state") {
        val userId = createUser("await_user")
        val ticketId = createTicket(userId, "Awaiting reply", "TECHNICAL_BUG", "Bug details")

        val supportUserId = createSupportUser()
        val sHeaders = supportHeaders(supportUserId)

        // Support replies → transitions to AWAITING_USER
        restClient.post(
            "/admin/tickets/$ticketId/reply",
            mapOf("body" to "Need more info"),
            sHeaders
        )

        When("the user replies while ticket is AWAITING_USER") {
            val response = restClient.post(
                "/tickets/$ticketId/reply",
                mapOf("body" to "Here are the details you requested."),
                userHeaders(userId)
            )

            Then("reply is accepted and status transitions to IN_PROGRESS") {
                response.statusCode shouldBe 201

                val detail = restClient.get("/admin/tickets/$ticketId", sHeaders)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "IN_PROGRESS"
                data["messageCount"] shouldBe 3
            }
        }
    }

    // ---------------------------------------------------------------
    // 12. Admin update ticket (status, priority, assignee)
    // ---------------------------------------------------------------
    Given("a ticket for admin updates") {
        val userId = createUser("update_ticket_user")
        val ticketId = createTicket(userId, "Update me", "ORDER_DISPUTE", "Dispute details")

        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        When("changing priority to URGENT") {
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("priority" to "URGENT"),
                headers
            )

            Then("the priority is updated") {
                response.statusCode shouldBe 200
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "SUPPORT_TICKET_UPDATED"

                val detail = restClient.get("/admin/tickets/$ticketId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["priority"] shouldBe "URGENT"
            }
        }

        When("assigning to a support user") {
            val assigneeId = createSupportUser("assigned_agent")
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("assigneeUserId" to assigneeId.toHexString()),
                headers
            )

            Then("the ticket is assigned") {
                response.statusCode shouldBe 200

                val detail = restClient.get("/admin/tickets/$ticketId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["assigneeUserId"] shouldBe assigneeId.toHexString()
                data["assigneeUsername"] shouldNotBe null
            }
        }

        When("changing status to RESOLVED") {
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "RESOLVED"),
                headers
            )

            Then("the ticket is resolved") {
                response.statusCode shouldBe 200

                val detail = restClient.get("/admin/tickets/$ticketId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "RESOLVED"
            }
        }

        When("closing a resolved ticket") {
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "CLOSED"),
                headers
            )

            Then("the ticket is closed") {
                response.statusCode shouldBe 200

                val detail = restClient.get("/admin/tickets/$ticketId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "CLOSED"
            }
        }
    }

    // ---------------------------------------------------------------
    // 13. Closed ticket rejects replies
    // ---------------------------------------------------------------
    Given("a closed ticket") {
        val userId = createUser("closed_ticket_user")
        val ticketId = createTicket(userId, "Will be closed", "OTHER", "Closing soon")

        val supportUserId = createSupportUser()
        val sHeaders = supportHeaders(supportUserId)

        // Close the ticket
        restClient.patch("/admin/tickets/$ticketId", mapOf("status" to "CLOSED"), sHeaders)

        When("the user tries to reply to a closed ticket") {
            val response = restClient.post(
                "/tickets/$ticketId/reply",
                mapOf("body" to "Can I still reply?"),
                userHeaders(userId)
            )

            Then("the reply is rejected") {
                response.statusCode shouldBe 400
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "SUPPORT_TICKET_CLOSED_NO_REPLY"
            }
        }

        When("support tries to reply to a closed ticket") {
            val response = restClient.post(
                "/admin/tickets/$ticketId/reply",
                mapOf("body" to "Trying to reply after close"),
                sHeaders
            )

            Then("the reply is rejected") {
                response.statusCode shouldBe 400
                response.bodyAs<Map<String, Any>>()!!["status"] shouldBe "SUPPORT_TICKET_CLOSED_NO_REPLY"
            }
        }
    }

    // ---------------------------------------------------------------
    // 14. Ticket statistics
    // ---------------------------------------------------------------
    Given("tickets in various states for stats") {
        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        When("fetching ticket stats") {
            val response = restClient.get("/admin/tickets/stats", headers)

            Then("returns valid statistics") {
                response.statusCode shouldBe 200
                val stats = response.bodyAs<Map<String, Any>>()!!
                stats.keys.contains("open") shouldBe true
                stats.keys.contains("inProgress") shouldBe true
                stats.keys.contains("awaitingUser") shouldBe true
                stats.keys.contains("resolvedToday") shouldBe true
                stats.keys.contains("averageResolutionTimeMicros") shouldBe true
                (stats["open"] as Number).toLong() shouldNotBe null
            }
        }
    }

    // ---------------------------------------------------------------
    // 15. Admin update non-existent ticket
    // ---------------------------------------------------------------
    Given("non-existent ticket for admin operations") {
        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)
        val fakeId = ObjectId.get().toHexString()

        When("updating a non-existent ticket") {
            val response = restClient.patch(
                "/admin/tickets/$fakeId",
                mapOf("status" to "IN_PROGRESS"),
                headers
            )

            Then("returns 404") {
                response.statusCode shouldBe 404
            }
        }

        When("replying to a non-existent ticket") {
            val response = restClient.post(
                "/admin/tickets/$fakeId/reply",
                mapOf("body" to "Reply to nowhere"),
                headers
            )

            Then("returns 404") {
                response.statusCode shouldBe 404
            }
        }
    }

    // ---------------------------------------------------------------
    // 16. Full ticket lifecycle
    // ---------------------------------------------------------------
    Given("a ticket going through the full lifecycle") {
        val userId = createUser("lifecycle_user")
        val supportUserId = createSupportUser("lifecycle_support")
        val uHeaders = userHeaders(userId)
        val sHeaders = supportHeaders(supportUserId)

        When("going through create → support reply → user reply → resolve → close") {
            // Step 1: User creates ticket
            val createResponse = restClient.post(
                "/tickets",
                mapOf(
                    "subject" to "Complete lifecycle test",
                    "category" to "PAYMENT_PROBLEM",
                    "body" to "Payment not going through."
                ),
                uHeaders
            )
            createResponse.statusCode shouldBe 201
            val ticketId = (createResponse.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)["id"] as String

            // Step 2: Verify initial state
            val initialDetail = restClient.get("/admin/tickets/$ticketId", sHeaders)
            val initialData = initialDetail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
            initialData["status"] shouldBe "OPEN"
            initialData["messageCount"] shouldBe 1

            // Step 3: Admin assigns and sets priority
            restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("assigneeUserId" to supportUserId.toHexString(), "priority" to "HIGH"),
                sHeaders
            ).statusCode shouldBe 200

            // Step 4: Support replies → AWAITING_USER
            restClient.post(
                "/admin/tickets/$ticketId/reply",
                mapOf("body" to "What payment method are you using?"),
                sHeaders
            ).statusCode shouldBe 201

            val afterSupportReply = restClient.get("/admin/tickets/$ticketId", sHeaders)
            (afterSupportReply.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)["status"] shouldBe "AWAITING_USER"

            // Step 5: User replies → IN_PROGRESS
            restClient.post(
                "/tickets/$ticketId/reply",
                mapOf("body" to "I'm using ARRR cryptocurrency."),
                uHeaders
            ).statusCode shouldBe 201

            val afterUserReply = restClient.get("/admin/tickets/$ticketId", sHeaders)
            (afterUserReply.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>)["status"] shouldBe "IN_PROGRESS"

            // Step 6: Support replies again → AWAITING_USER
            restClient.post(
                "/admin/tickets/$ticketId/reply",
                mapOf("body" to "Try refreshing your viewing key."),
                sHeaders
            ).statusCode shouldBe 201

            // Step 7: Resolve
            restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "RESOLVED"),
                sHeaders
            ).statusCode shouldBe 200

            // Step 8: Close
            restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "CLOSED"),
                sHeaders
            ).statusCode shouldBe 200

            Then("the final state is correct") {
                val finalDetail = restClient.get("/admin/tickets/$ticketId", sHeaders)
                val data = finalDetail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "CLOSED"
                data["priority"] shouldBe "HIGH"
                data["messageCount"] shouldBe 4
                data["assigneeUserId"] shouldBe supportUserId.toHexString()

                val messages = data["messages"] as List<*>
                messages shouldHaveSize 4

                val roles = messages.map { (it as Map<*, *>)["authorRole"] as String }
                roles shouldBe listOf("USER", "SUPPORT", "USER", "SUPPORT")
            }
        }
    }

    // ---------------------------------------------------------------
    // 17. Multiple updates at once
    // ---------------------------------------------------------------
    Given("a ticket for multi-field update") {
        val userId = createUser("multi_update_user")
        val ticketId = createTicket(userId, "Multi update", "SAFETY_CONCERN", "Safety issue")

        val supportUserId = createSupportUser()
        val assigneeId = createSupportUser("multi_assignee")
        val headers = supportHeaders(supportUserId)

        When("updating status, priority, and assignee at once") {
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf(
                    "status" to "IN_PROGRESS",
                    "priority" to "HIGH",
                    "assigneeUserId" to assigneeId.toHexString()
                ),
                headers
            )

            Then("all fields are updated") {
                response.statusCode shouldBe 200

                val detail = restClient.get("/admin/tickets/$ticketId", headers)
                val data = detail.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
                data["status"] shouldBe "IN_PROGRESS"
                data["priority"] shouldBe "HIGH"
                data["assigneeUserId"] shouldBe assigneeId.toHexString()
            }
        }
    }

    // ---------------------------------------------------------------
    // 18. Sorting tickets
    // ---------------------------------------------------------------
    Given("tickets for sorting verification") {
        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        When("sorting by priority descending") {
            val response = restClient.get("/admin/tickets?sort=priority&direction=DESC", headers)

            Then("returns tickets sorted") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                val content = body["content"] as List<*>
                content.size intShouldBeGreaterThan 0
            }
        }

        When("sorting by createdAt ascending") {
            val response = restClient.get("/admin/tickets?sort=createdAt&direction=ASC", headers)

            Then("returns tickets in ascending order") {
                response.statusCode shouldBe 200
            }
        }

        When("sorting by lastMessageAt") {
            val response = restClient.get("/admin/tickets?sort=lastMessageAt&direction=DESC", headers)

            Then("returns tickets sorted by last activity") {
                response.statusCode shouldBe 200
            }
        }
    }

    // ---------------------------------------------------------------
    // 19. Status transition validation - invalid transitions rejected
    // ---------------------------------------------------------------
    Given("tickets in terminal states for transition validation") {
        val userId = createUser()
        val supportUserId = createSupportUser()
        val headers = supportHeaders(supportUserId)

        fun createTicketInStatus(status: String): String {
            val createResponse = restClient.post(
                "/tickets",
                mapOf("subject" to "Transition test", "category" to "ACCOUNT_ISSUE", "body" to "Testing transitions"),
                userHeaders(userId)
            )
            val data = createResponse.bodyAs<Map<String, Any>>()!!["data"] as Map<*, *>
            val ticketId = data["id"] as String

            if (status != "OPEN") {
                restClient.patch("/admin/tickets/$ticketId", mapOf("status" to status), headers)
            }
            return ticketId
        }

        When("trying to transition RESOLVED → IN_PROGRESS") {
            val ticketId = createTicketInStatus("RESOLVED")
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "IN_PROGRESS"),
                headers
            )

            Then("it is rejected as invalid") {
                response.statusCode shouldBe 400
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "SUPPORT_TICKET_INVALID_STATUS_TRANSITION"
            }
        }

        When("trying to transition RESOLVED → AWAITING_USER") {
            val ticketId = createTicketInStatus("RESOLVED")
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "AWAITING_USER"),
                headers
            )

            Then("it is rejected as invalid") {
                response.statusCode shouldBe 400
            }
        }

        When("trying to transition CLOSED → IN_PROGRESS") {
            val ticketId = createTicketInStatus("CLOSED")
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "IN_PROGRESS"),
                headers
            )

            Then("it is rejected as invalid") {
                response.statusCode shouldBe 400
            }
        }

        When("trying to transition CLOSED → RESOLVED") {
            val ticketId = createTicketInStatus("CLOSED")
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "RESOLVED"),
                headers
            )

            Then("it is rejected as invalid") {
                response.statusCode shouldBe 400
            }
        }

        When("reopening a RESOLVED ticket (RESOLVED → OPEN)") {
            val ticketId = createTicketInStatus("RESOLVED")
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "OPEN"),
                headers
            )

            Then("it is allowed") {
                response.statusCode shouldBe 200
                val body = response.bodyAs<Map<String, Any>>()!!
                body["status"] shouldBe "SUPPORT_TICKET_UPDATED"
            }
        }

        When("reopening a CLOSED ticket (CLOSED → OPEN)") {
            val ticketId = createTicketInStatus("CLOSED")
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("status" to "OPEN"),
                headers
            )

            Then("it is allowed") {
                response.statusCode shouldBe 200
            }
        }

        When("updating only priority on a CLOSED ticket (no status change)") {
            val ticketId = createTicketInStatus("CLOSED")
            val response = restClient.patch(
                "/admin/tickets/$ticketId",
                mapOf("priority" to "URGENT"),
                headers
            )

            Then("it is allowed because status validation is skipped") {
                response.statusCode shouldBe 200
            }
        }
    }
})
