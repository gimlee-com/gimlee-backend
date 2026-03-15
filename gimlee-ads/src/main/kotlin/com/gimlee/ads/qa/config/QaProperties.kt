package com.gimlee.ads.qa.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gimlee.ads.qa")
data class QaProperties(
    val questionMaxLength: Int = 500,
    val answerMaxLength: Int = 2000,
    val maxUnansweredPerUserPerAd: Int = 3,
    val maxQuestionsPerAd: Int = 100,
    val maxCommunityAnswersPerQuestion: Int = 3,
    val maxPinnedPerAd: Int = 3,
    val cooldownSeconds: Long = 300,
    val upvoteMilestones: List<Int> = listOf(5, 10, 25, 50, 100)
)
