package com.gimlee.api.playground.web

import com.gimlee.api.playground.ads.data.AdsPopulator
import com.gimlee.api.playground.media.data.MediaPopulator
import com.gimlee.api.playground.users.data.UsersPopulator
import com.gimlee.common.domain.model.StatusCode
import com.gimlee.common.web.dto.StatusResponseDto
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@Profile("local")
@RestController
class PlaygroundController(
    @Lazy private val usersPopulator: UsersPopulator,
    @Lazy private val mediaPopulator: MediaPopulator,
    @Lazy private val adsPopulator: AdsPopulator
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

    // @Privileged(role = "ADMIN")
    @PostMapping("/playground/createAds")
    fun createAds(): StatusResponseDto {
        adsPopulator.populateAds()
        return StatusResponseDto.fromStatusCode(StatusCode.SUCCESS)
    }
}