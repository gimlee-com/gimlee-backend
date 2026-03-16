package com.gimlee.ads.qa.web

import com.gimlee.ads.qa.domain.QuestionService
import com.gimlee.ads.qa.web.dto.response.QaStatsDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Questions & Answers", description = "Public Q&A endpoints for ad listings")
@RestController
@RequestMapping("/qa")
class QaController(
    private val questionService: QuestionService
) {

    @Operation(summary = "Get Q&A Stats", description = "Returns Q&A statistics for an ad. Public endpoint.")
    @ApiResponse(responseCode = "200", description = "Q&A statistics")
    @GetMapping("/ads/{adId}/questions/stats")
    fun getQaStats(@PathVariable adId: String): ResponseEntity<QaStatsDto> {
        val (answered, unanswered) = questionService.getQaStats(adId)
        return ResponseEntity.ok(QaStatsDto(totalAnswered = answered, totalUnanswered = unanswered))
    }
}
