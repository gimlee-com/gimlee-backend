package com.gimlee.notifications.domain.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Represents a suggested action for the user to take when interacting with a notification")
data class SuggestedAction(
    @Schema(description = "The type of action to perform")
    val type: SuggestedActionType,
    @Schema(description = "The target identifier for the action (e.g., an order ID or ad ID)", example = "019483...")
    val target: String? = null
)

@Schema(description = "Enumeration of possible suggested action types")
enum class SuggestedActionType(val shortName: String) {
    @Schema(description = "Navigate to order details")
    ORDER_DETAILS("OD"),
    @Schema(description = "Navigate to purchase list")
    PURCHASE_LIST("PL"),
    @Schema(description = "Navigate to ad details")
    AD_DETAILS("AD"),
    @Schema(description = "Navigate to seller ad details")
    SELLER_AD_DETAILS("SAD"),
    @Schema(description = "Navigate to ad edit page")
    AD_EDIT("AE"),
    @Schema(description = "Navigate to question/answer details")
    QA_DETAILS("QA"),
    @Schema(description = "Navigate to admin report details")
    ADMIN_REPORT_DETAILS("ARD"),
    @Schema(description = "Navigate to admin ticket details")
    ADMIN_TICKET_DETAILS("ATD"),
    @Schema(description = "Navigate to ticket details")
    TICKET_DETAILS("TD"),
    @Schema(description = "Navigate to support center")
    SUPPORT_CENTER("SC"),
    @Schema(description = "Navigate to getting started guide")
    GETTING_STARTED("GS");

    companion object {
        fun fromShortName(shortName: String): SuggestedActionType =
            entries.find { it.shortName == shortName } ?: throw IllegalArgumentException("Unknown SuggestedActionType: $shortName")
    }
}
