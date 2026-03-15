package com.gimlee.ads.qa.web

import com.gimlee.ads.qa.domain.AnswerService
import com.gimlee.ads.qa.domain.QuestionService
import com.gimlee.ads.qa.domain.UpvoteService
import com.gimlee.ads.qa.domain.model.Answer
import com.gimlee.ads.qa.domain.model.Question
import com.gimlee.ads.qa.web.dto.request.QaSortBy
import com.gimlee.ads.qa.web.dto.response.AnswerDto
import com.gimlee.ads.qa.web.dto.response.QaStatsDto
import com.gimlee.ads.qa.web.dto.response.QuestionDto
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Questions & Answers", description = "Public Q&A endpoints for ad listings")
@RestController
@RequestMapping("/qa")
class QaController(
    private val questionService: QuestionService,
    private val answerService: AnswerService,
    private val upvoteService: UpvoteService
) {

    @Operation(
        summary = "List Answered Questions",
        description = "Returns paginated answered Q&A pairs for an ad, with pinned items first. Public endpoint."
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

        val mappedPage = questionsPage.map { q -> toQuestionDto(q, answersMap, upvotedIds) }
        return ResponseEntity.ok(mappedPage)
    }

    @Operation(summary = "Get Q&A Stats", description = "Returns Q&A statistics for an ad. Public endpoint.")
    @ApiResponse(responseCode = "200", description = "Q&A statistics")
    @GetMapping("/ads/{adId}/questions/stats")
    fun getQaStats(@PathVariable adId: String): ResponseEntity<QaStatsDto> {
        val (answered, unanswered) = questionService.getQaStats(adId)
        return ResponseEntity.ok(QaStatsDto(totalAnswered = answered, totalUnanswered = unanswered))
    }

    private fun toQuestionDto(
        question: Question,
        answersMap: Map<String, List<Answer>>,
        upvotedIds: Set<String>
    ): QuestionDto {
        val answers = answersMap[question.id]?.map { toAnswerDto(it) } ?: emptyList()
        return QuestionDto(
            id = question.id,
            adId = question.adId,
            authorId = question.authorId,
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

    private fun toAnswerDto(answer: Answer): AnswerDto {
        return AnswerDto(
            id = answer.id,
            questionId = answer.questionId,
            authorId = answer.authorId,
            type = answer.type,
            text = answer.text,
            createdAt = answer.createdAt,
            updatedAt = answer.updatedAt
        )
    }
}
