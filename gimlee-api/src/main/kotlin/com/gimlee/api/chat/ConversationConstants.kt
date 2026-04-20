package com.gimlee.api.chat

/**
 * Gimlee-specific conversation type constants.
 * The chat module treats these as opaque strings.
 */
object ConversationTypes {
    const val ORDER = "ORDER"
}

/**
 * Gimlee-specific conversation link type constants.
 * The chat module stores/queries these but never interprets them.
 */
object ConversationLinkTypes {
    const val PURCHASE = "PRC"
}
