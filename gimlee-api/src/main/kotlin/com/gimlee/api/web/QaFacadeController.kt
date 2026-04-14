package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.qa.domain.AnswerService
import com.gimlee.ads.qa.domain.QaOutcome
import com.gimlee.ads.qa.domain.QuestionService
import com.gimlee.ads.qa.domain.UpvoteService
import com.gimlee.ads.qa.domain.model.Answer
import com.gimlee.ads.qa.domain.model.Question
import com.gimlee.ads.qa.web.dto.request.AskQuestionRequestDto
import com.gimlee.ads.qa.web.dto.request.EditAnswerRequestDto
import com.gimlee.ads.qa.web.dto.request.QaSortBy
import com.gimlee.ads.qa.web.dto.request.SubmitAnswerRequestDto
import com.gimlee.ads.qa.web.dto.response.AnswerDto
import com.gimlee.ads.qa.web.dto.response.QuestionDto
import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.service.UserService
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.purchases.domain.PurchaseService
import com.gimlee.user.domain.ProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Questions & Answers", description = "Authenticated Q&A endpoints for ad listings")
@RestController
@RequestMapping("/qa")
class QaFacadeController(
    private val questionService: QuestionService,
    private val answerService: AnswerService,
    private val upvoteService: UpvoteService,
    private val adService: AdService,
    private val purchaseService: PurchaseService,
    private val userService: UserService,
    private val profileService: ProfileService,
    private val messageSource: MessageSource
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "List Answered Questions",
        description = "Returns paginated answered Q&A pairs for an ad, with pinned items first. Public endpoint — enriched with author usernames and avatars."
    )
    @ApiResponse(responseCode = "200", description = "Page of answered questions with their answers")
    @GetMapping("/ads/{adId}/questions")
    fun getPublicQuestions(
        @PathVariable adId: String,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "10") size: Int,
        @Parameter(description = "Sort order") @RequestParam(defaultValue = "UPVOTES") sort: QaSortBy
    ): ResponseEntity<Page<QuestionDto>> {
        val pageable = PageRequest.of(page, size.coerceIn(1, 50))

        val pinnedQuestions = questionService.getPinnedQuestions(adId)
        val questionsPage = questionService.getPublicQuestions(adId, pageable, sort.name)

        val allQuestionIds = (pinnedQuestions.map { it.id } + questionsPage.content.map { it.id }).distinct()
        val answersMap = answerService.getAnswersForQuestions(allQuestionIds)

        val currentUserId = HttpServletRequestAuthUtil.getPrincipalOrNull()?.userId?.takeIf { it.isNotBlank() }
        val upvotedIds = if (currentUserId != null) {
            upvoteService.getUserUpvotes(currentUserId, allQuestionIds)
        } else emptySet()

        val allUserIds = collectUserIds(questionsPage.content, answersMap)
        val usernames = userService.findUsernamesByIds(allUserIds)
        val avatars = profileService.getAvatarsByUserIds(allUserIds)

        val mappedPage = questionsPage.map { q -> toQuestionDto(q, answersMap, upvotedIds, usernames, avatars) }
        return ResponseEntity.ok(mappedPage)
    }

    @Operation(
        summary = "Ask a Question",
        description = "Submit a question on an ad listing. The caller must not be the ad owner."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Question submitted successfully",
        content = [Content(schema = Schema(implementation = QuestionDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Cannot ask on own ad. Possible status codes: QUESTION_OWN_AD",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Ad not found. Possible status codes: QUESTION_AD_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "429",
        description = "Rate limited. Possible status codes: QUESTION_LIMIT_REACHED, QUESTION_COOLDOWN_ACTIVE",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/ads/{adId}/questions")
    @Privileged(role = "USER")
    fun askQuestion(
        @Parameter(description = "Ad ID") @PathVariable adId: String,
        @Valid @RequestBody request: AskQuestionRequestDto
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val ad = adService.getAd(adId) ?: return handleOutcome(QaOutcome.QUESTION_AD_NOT_FOUND)

        if (ad.userId == principal.userId) {
            return handleOutcome(QaOutcome.QUESTION_OWN_AD)
        }

        val (outcome, question) = questionService.askQuestion(adId, principal.userId, ad.userId, ad.title, request.text)
        if (question == null) return handleOutcome(outcome)

        val usernames = userService.findUsernamesByIds(listOf(principal.userId))
        val avatars = profileService.getAvatarsByUserIds(listOf(principal.userId))
        return handleOutcome(outcome, toQuestionDto(question, emptyMap(), emptySet(), usernames, avatars))
    }

    @Operation(
        summary = "Submit an Answer",
        description = "Submit an answer to a question. Automatically determines SELLER or COMMUNITY type based on ad ownership and purchase history."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Answer submitted successfully",
        content = [Content(schema = Schema(implementation = AnswerDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not a previous buyer (for community answers). Possible status codes: ANSWER_NOT_PREVIOUS_BUYER",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Seller already answered. Possible status codes: ANSWER_ALREADY_EXISTS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/questions/{questionId}/answers")
    @Privileged(role = "USER")
    fun submitAnswer(
        @Parameter(description = "Question ID") @PathVariable questionId: String,
        @Valid @RequestBody request: SubmitAnswerRequestDto
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()

        val question = questionService.getQuestion(questionId)
            ?: return handleOutcome(QaOutcome.QUESTION_NOT_FOUND)
        val ad = adService.getAd(question.adId)
            ?: return handleOutcome(QaOutcome.QUESTION_AD_NOT_FOUND)

        val isSeller = ad.userId == principal.userId
        val (outcome, answer) = if (isSeller) {
            answerService.submitSellerAnswer(questionId, principal.userId, ad.title, request.text)
        } else {
            val isPreviousBuyer = purchaseService.hasCompletedPurchaseForAd(
                ObjectId(principal.userId), ObjectId(question.adId)
            )
            if (!isPreviousBuyer) {
                return handleOutcome(QaOutcome.ANSWER_NOT_PREVIOUS_BUYER)
            }
            answerService.submitCommunityAnswer(questionId, principal.userId, ad.title, request.text)
        }

        if (answer == null) return handleOutcome(outcome)

        val usernames = userService.findUsernamesByIds(listOf(principal.userId))
        val avatars = profileService.getAvatarsByUserIds(listOf(principal.userId))
        return handleOutcome(outcome, toAnswerDto(answer, usernames, avatars))
    }

    @Operation(
        summary = "Edit an Answer",
        description = "Edit an existing answer. Only the answer author can edit."
    )
    @ApiResponse(responseCode = "200", description = "Answer updated successfully")
    @ApiResponse(
        responseCode = "403",
        description = "Not answer owner. Possible status codes: ANSWER_NOT_OWNER",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PutMapping("/answers/{answerId}")
    @Privileged(role = "USER")
    fun editAnswer(
        @Parameter(description = "Answer ID") @PathVariable answerId: String,
        @Valid @RequestBody request: EditAnswerRequestDto
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val (outcome, answer) = answerService.editAnswer(answerId, principal.userId, request.text)
        if (answer == null) return handleOutcome(outcome)

        val usernames = userService.findUsernamesByIds(listOf(principal.userId))
        val avatars = profileService.getAvatarsByUserIds(listOf(principal.userId))
        return handleOutcome(outcome, toAnswerDto(answer, usernames, avatars))
    }

    @Operation(
        summary = "Toggle Upvote",
        description = "Toggle upvote on a question. If already upvoted, removes the upvote."
    )
    @ApiResponse(responseCode = "200", description = "Upvote toggled")
    @PostMapping("/questions/{questionId}/upvote")
    @Privileged(role = "USER")
    fun toggleUpvote(
        @Parameter(description = "Question ID") @PathVariable questionId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()

        val question = questionService.getQuestion(questionId)
            ?: return handleOutcome(QaOutcome.QUESTION_NOT_FOUND)
        val ad = adService.getAd(question.adId)
            ?: return handleOutcome(QaOutcome.QUESTION_AD_NOT_FOUND)

        val outcome = upvoteService.toggleUpvote(questionId, principal.userId, ad.userId, ad.title)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Toggle Pin",
        description = "Toggle pin status of a question. Only the ad owner (seller) can pin."
    )
    @ApiResponse(responseCode = "200", description = "Pin status toggled")
    @ApiResponse(
        responseCode = "400",
        description = "Pin limit reached. Possible status codes: PIN_LIMIT_REACHED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not the ad owner. Possible status codes: NOT_AD_OWNER",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PutMapping("/questions/{questionId}/pin")
    @Privileged(role = "USER")
    fun togglePin(
        @Parameter(description = "Question ID") @PathVariable questionId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()

        val question = questionService.getQuestion(questionId)
            ?: return handleOutcome(QaOutcome.QUESTION_NOT_FOUND)
        val ad = adService.getAd(question.adId)
            ?: return handleOutcome(QaOutcome.QUESTION_AD_NOT_FOUND)

        if (ad.userId != principal.userId) {
            return handleOutcome(QaOutcome.NOT_AD_OWNER)
        }

        val outcome = questionService.togglePin(questionId, question.adId)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Hide a Question",
        description = "Hide a question from public view. Only the ad owner (seller) can hide."
    )
    @ApiResponse(responseCode = "200", description = "Question hidden")
    @ApiResponse(
        responseCode = "403",
        description = "Not the ad owner. Possible status codes: NOT_AD_OWNER",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PutMapping("/questions/{questionId}/hide")
    @Privileged(role = "USER")
    fun hideQuestion(
        @Parameter(description = "Question ID") @PathVariable questionId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()

        val question = questionService.getQuestion(questionId)
            ?: return handleOutcome(QaOutcome.QUESTION_NOT_FOUND)
        val ad = adService.getAd(question.adId)
            ?: return handleOutcome(QaOutcome.QUESTION_AD_NOT_FOUND)

        if (ad.userId != principal.userId) {
            return handleOutcome(QaOutcome.NOT_AD_OWNER)
        }

        val outcome = questionService.hideQuestion(questionId)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Remove a Question",
        description = "Remove a question permanently. Admin/moderator only."
    )
    @ApiResponse(responseCode = "200", description = "Question removed")
    @DeleteMapping("/questions/{questionId}")
    @Privileged(role = "ADMIN")
    fun removeQuestion(
        @Parameter(description = "Question ID") @PathVariable questionId: String
    ): ResponseEntity<Any> {
        val outcome = questionService.removeQuestion(questionId)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Seller Unanswered Questions",
        description = "Returns unanswered questions for the seller's ad. Caller must be the ad owner."
    )
    @ApiResponse(responseCode = "200", description = "Paginated list of unanswered questions")
    @ApiResponse(
        responseCode = "403",
        description = "Not the ad owner. Possible status codes: NOT_AD_OWNER",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/ads/{adId}/questions/seller")
    @Privileged(role = "USER")
    fun getSellerUnanswered(
        @Parameter(description = "Ad ID") @PathVariable adId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val ad = adService.getAd(adId) ?: return handleOutcome(QaOutcome.QUESTION_AD_NOT_FOUND)

        if (ad.userId != principal.userId) {
            return handleOutcome(QaOutcome.NOT_AD_OWNER)
        }

        val pageable = PageRequest.of(page, size.coerceIn(1, 50))
        val questionsPage = questionService.getUnansweredQuestions(adId, pageable)

        val userIds = questionsPage.content.map { it.authorId }.distinct()
        val usernames = userService.findUsernamesByIds(userIds)
        val avatars = profileService.getAvatarsByUserIds(userIds)

        val mapped = questionsPage.map { q -> toQuestionDto(q, emptyMap(), emptySet(), usernames, avatars) }
        return ResponseEntity.ok(mapped)
    }

    @Operation(
        summary = "My Unanswered Questions",
        description = "Returns the caller's own unanswered (pending) questions on an ad."
    )
    @ApiResponse(responseCode = "200", description = "List of own unanswered questions")
    @GetMapping("/ads/{adId}/questions/mine")
    @Privileged(role = "USER")
    fun getMyUnanswered(
        @Parameter(description = "Ad ID") @PathVariable adId: String
    ): ResponseEntity<List<QuestionDto>> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val questions = questionService.getOwnUnansweredQuestions(adId, principal.userId)

        val usernames = userService.findUsernamesByIds(listOf(principal.userId))
        val avatars = profileService.getAvatarsByUserIds(listOf(principal.userId))
        val dtos = questions.map { q -> toQuestionDto(q, emptyMap(), emptySet(), usernames, avatars) }
        return ResponseEntity.ok(dtos)
    }

    private fun collectUserIds(questions: List<Question>, answersMap: Map<String, List<Answer>>): List<String> {
        val questionAuthorIds = questions.map { it.authorId }
        val answerAuthorIds = answersMap.values.flatten().map { it.authorId }
        return (questionAuthorIds + answerAuthorIds).distinct()
    }

    private fun toQuestionDto(
        question: Question,
        answersMap: Map<String, List<Answer>>,
        upvotedIds: Set<String>,
        usernames: Map<String, String>,
        avatars: Map<String, String?>
    ): QuestionDto {
        val answers = answersMap[question.id]?.map { toAnswerDto(it, usernames, avatars) } ?: emptyList()
        return QuestionDto(
            id = question.id,
            adId = question.adId,
            authorId = question.authorId,
            authorUsername = usernames[question.authorId],
            authorAvatarUrl = avatars[question.authorId],
            text = question.text,
            upvoteCount = question.upvoteCount,
            isPinned = question.isPinned,
            isUpvotedByMe = question.id in upvotedIds,
            status = question.status,
            answerCount = answers.size,
            answers = answers,
            createdAt = question.createdAt,
            updatedAt = question.updatedAt
        )
    }

    private fun toAnswerDto(answer: Answer, usernames: Map<String, String>, avatars: Map<String, String?>): AnswerDto {
        return AnswerDto(
            id = answer.id,
            questionId = answer.questionId,
            authorId = answer.authorId,
            authorUsername = usernames[answer.authorId],
            authorAvatarUrl = avatars[answer.authorId],
            type = answer.type,
            text = answer.text,
            createdAt = answer.createdAt,
            updatedAt = answer.updatedAt
        )
    }
}
