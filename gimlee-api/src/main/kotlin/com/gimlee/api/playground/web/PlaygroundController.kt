package com.gimlee.api.playground.web

import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import com.gimlee.api.domain.StatusCode
import com.gimlee.api.playground.media.data.MediaPopulator
import com.gimlee.api.playground.users.data.UsersPopulator
import com.gimlee.api.web.dto.StatusResponseDto

@Profile("local")
@RestController
class PlaygroundController(
    @Lazy private val usersPopulator: UsersPopulator,
    @Lazy private val mediaPopulator: MediaPopulator,
) {
    //@Privileged(role = "ADMIN")
    @PostMapping("/playground/createUsers")
    fun createUsers(): StatusResponseDto {
        usersPopulator.populateUsers()
        return StatusResponseDto.fromStatusCode(StatusCode.SUCCESS)
    }

   // @Privileged(role = "ADMIN")
    @PostMapping("/playground/createMedia")
    fun createMedia(): StatusResponseDto {
        mediaPopulator.populateMedia()
        return StatusResponseDto.fromStatusCode(StatusCode.SUCCESS)
    }
}