package com.gimlee.api.playground.web

import com.gimlee.api.playground.ads.data.AdsPopulator
import com.gimlee.api.playground.data.DatabaseCleaner
import com.gimlee.api.playground.media.data.MediaPopulator
import com.gimlee.api.playground.users.data.UsersPopulator
import com.gimlee.api.playground.payments.service.YcashFaucetService
import com.gimlee.api.playground.web.dto.CreateUsersRequest
import com.gimlee.api.playground.web.dto.FaucetRequest
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.web.dto.StatusResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Profile("local", "dev", "test")
@Tag(name = "Playground", description = "Endpoints for populating test data (only available in local/dev/test profiles)")
@RestController
class PlaygroundController(
    @Lazy private val usersPopulator: UsersPopulator,
    @Lazy private val mediaPopulator: MediaPopulator,
    @Lazy private val adsPopulator: AdsPopulator,
    @Lazy private val databaseCleaner: DatabaseCleaner,
    @Lazy private val ycashFaucetService: YcashFaucetService
) {
    @Operation(summary = "Create Playground Users", description = "Populates the database with a set of test users. If view keys are provided, 'pirate_seller' and/or 'ycash_seller' users are created.")
    @ApiResponse(
        responseCode = "200",
        description = "Users created successfully. Possible status codes: SUCCESS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/playground/createUsers")
    fun createUsers(@RequestBody request: CreateUsersRequest? = null): StatusResponseDto {
        usersPopulator.populateUsers(request?.pirateViewKey, request?.ycashViewKey)
        return StatusResponseDto.fromOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(summary = "Ycash Faucet", description = "Sends some YEC coins to the specified address.")
    @ApiResponse(
        responseCode = "200",
        description = "Coins sent successfully. Possible status codes: SUCCESS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/playground/ycash/faucet")
    fun ycashFaucet(@RequestBody request: FaucetRequest): StatusResponseDto {
        val operationId = ycashFaucetService.sendCoins(request.address, request.amount)
        return StatusResponseDto.fromOutcome(CommonOutcome.SUCCESS, data = mapOf("operationId" to operationId))
    }

    @Operation(summary = "Create Playground Media", description = "Populates the media store with test images.")
    @ApiResponse(
        responseCode = "200",
        description = "Media created successfully. Possible status codes: SUCCESS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/playground/createMedia")
    fun createMedia(): StatusResponseDto {
        mediaPopulator.populateMedia()
        return StatusResponseDto.fromOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(summary = "Create Playground Ads", description = "Populates the database with test advertisements.")
    @ApiResponse(
        responseCode = "200",
        description = "Ads created successfully. Possible status codes: SUCCESS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/playground/createAds")
    fun createAds(): StatusResponseDto {
        adsPopulator.populateAds()
        return StatusResponseDto.fromOutcome(CommonOutcome.SUCCESS)
    }

    @Operation(summary = "Clear Database", description = "Empties all collections in the database (preserving indexes).")
    @ApiResponse(
        responseCode = "200",
        description = "Database cleared successfully. Possible status codes: SUCCESS",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/playground/clearDatabase")
    fun clearDatabase(): StatusResponseDto {
        databaseCleaner.clearAll()
        return StatusResponseDto.fromOutcome(CommonOutcome.SUCCESS)
    }
}