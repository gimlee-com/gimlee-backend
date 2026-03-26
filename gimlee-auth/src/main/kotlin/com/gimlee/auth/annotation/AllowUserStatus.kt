package com.gimlee.auth.annotation

import com.gimlee.auth.domain.UserStatus

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class AllowUserStatus(vararg val statuses: UserStatus)
