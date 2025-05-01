package com.gimlee.api.playground.users

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvParser
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.gimlee.auth.model.Role
import com.gimlee.auth.util.createHexSaltAndPasswordHash
import com.gimlee.auth.util.generateSalt
import com.gimlee.api.auth.domain.UserStatus
import com.gimlee.api.playground.users.domain.User
import java.io.InputStream
import java.time.LocalDateTime

private const val PASSWORD = "Password1"
private const val PHONE = "123456789"
private const val EMAIL = "qbns.spam@gmail.com"

private val usersResource: InputStream = (object {}).javaClass.classLoader.getResourceAsStream("playground/users.csv")!!
private val mapper: CsvMapper = CsvMapper().enable(CsvParser.Feature.WRAP_AS_ARRAY)
private val users = mapper
    .readerFor(User::class.java)
    .with(CsvSchema.emptySchema().withHeader().withColumnSeparator(','))
    .readValues<User>(usersResource)
    .readAll()

fun createUsers() = users.map { user ->
    val (salt, passwordHash) = createHexSaltAndPasswordHash(PASSWORD, generateSalt())
    Pair(com.gimlee.api.auth.domain.User(
        username = user.username,
        displayName = user.username,
        phone = PHONE,
        email = EMAIL,
        password = passwordHash,
        passwordSalt = salt,
        status = UserStatus.ACTIVE,
        lastLogin = LocalDateTime.now()
    ), user.roles.split(";").map { role -> Role.valueOf(role) })
}
