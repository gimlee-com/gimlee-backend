package com.gimlee.notifications.domain

/**
 * Provides the list of admin user IDs for admin notification fan-out.
 * Implemented by the application layer to decouple gimlee-notifications
 * from gimlee-auth.
 */
interface AdminUserProvider {
    fun getAdminUserIds(): List<String>
}
