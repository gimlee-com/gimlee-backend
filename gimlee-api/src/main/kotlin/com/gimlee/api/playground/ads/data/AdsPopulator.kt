package com.gimlee.api.playground.ads.data

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.location.cities.data.cityDataUnsorted // Import the city data list
import com.gimlee.api.playground.ads.domain.AdTemplate
import com.gimlee.api.playground.media.data.PlaygroundMediaRepository // Import PlaygroundMediaRepository
import com.gimlee.auth.domain.User // Assuming this is the correct User class
import com.gimlee.auth.persistence.UserRepository
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.logging.log4j.LogManager
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.random.Random

@Lazy(true)
@Component
class AdsPopulator(
    private val adService: AdService,
    private val userRepository: UserRepository, // To get all users
    private val playgroundMediaRepository: PlaygroundMediaRepository // Inject PlaygroundMediaRepository
) {
    companion object {
        private val log = LogManager.getLogger()
        private const val ADS_TEMPLATE_RESOURCE_PATH = "/playground/ads.tsv"
        private const val MIN_PRICE = 10.0
        private const val MAX_PRICE = 5000.0
        private const val ADS_CHECK_USER = "admin" // User to check if ads potentially exist
        private const val THREAD_POOL_SIZE = 8 // Dedicated thread pool size
        private const val MEDIA_ADD_CHANCE = 0.7 // 70% chance to add media to an ad
        private const val MAX_MEDIA_ITEMS_PER_AD = 20 // Max number of media items to add if chosen
    }

    // Load ad templates once
    private val adTemplates: List<AdTemplate> by lazy { loadAdTemplates() }

    // Executor service for handling ad creation in parallel
    private lateinit var executorService: ExecutorService

    @PostConstruct
    fun initialize() {
        log.info("Initializing AdsPopulator thread pool with size $THREAD_POOL_SIZE...")
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
    }

    @PreDestroy
    fun destroy() {
        shutdownAndAwaitTermination()
    }


    private fun loadAdTemplates(): List<AdTemplate> {
        log.info("Loading ad templates from $ADS_TEMPLATE_RESOURCE_PATH")
        val adsStream: InputStream? = javaClass.getResourceAsStream(ADS_TEMPLATE_RESOURCE_PATH)
        if (adsStream == null) {
            log.error("Could not find ads template resource: $ADS_TEMPLATE_RESOURCE_PATH")
            return emptyList()
        }

        val mapper = CsvMapper()
        val schema = CsvSchema.emptySchema()
            .withHeader()
            .withColumnSeparator('\t')
            .withoutQuoteChar()

        return try {
            mapper.readerFor(AdTemplate::class.java).with(schema)
                .readValues<AdTemplate>(adsStream).readAll()
                .also { log.info("Loaded ${it.size} ad templates.") }
        } catch (e: Exception) {
            log.error("Failed to load ad templates from $ADS_TEMPLATE_RESOURCE_PATH", e)
            emptyList()
        }
    }

    fun populateAds() {
        if (adTemplates.isEmpty()) {
            log.warn("No ad templates loaded. Skipping ad population.")
            return
        }

        val sellerUser = userRepository.findOneByField("username", "seller")
        if (sellerUser != null) {
            log.info("User 'seller' found. Creating 1000 ads for 'seller' with 'Buyable' prefix.")
            val totalAds = 1000
            val chunkSize = 100
            val tasks = (1..(totalAds / chunkSize)).map {
                executorService.submit {
                    createAdsForUser(sellerUser, chunkSize, "Buyable ")
                }
            }
            tasks.forEach { it.get() }
            log.info("Finished creating 1000 ads for 'seller'.")
            return
        }

        // Check for admin user remains the same
        val adminUser = userRepository.findOneByField("username", ADS_CHECK_USER)
        if (adminUser != null) {
            log.warn("Playground ads might already exist. Proceeding with potential duplicates.")
        } else {
            log.info("Admin user not found, likely first run. Proceeding with ad population.")
        }

        val allUsers = userRepository.findAll()
        if (allUsers.isEmpty()) {
            log.warn("No users found in the database. Cannot populate ads.")
            return
        }

        val totalUsers = allUsers.size
        log.info("Total users found: $totalUsers. Calculating ad distribution.")

        // User distribution logic remains the same
        val count50to1000 = ceil(totalUsers * 0.005).toInt()
        val count10to50 = ceil(totalUsers * 0.01).toInt()
        val count1to9 = ceil(totalUsers * 0.1).toInt()

        val availableUsers = allUsers.toMutableList()
        availableUsers.shuffle()

        val usersFor50to1000 = availableUsers.take(count50to1000)
        availableUsers.removeAll(usersFor50to1000.toSet())

        val usersFor10to50 = availableUsers.take(count10to50)
        availableUsers.removeAll(usersFor10to50.toSet())

        val usersFor1to9 = availableUsers.take(count1to9)
        availableUsers.removeAll(usersFor1to9.toSet())

        log.info("Assigning ads: ${usersFor50to1000.size} users (50-1000 ads), ${usersFor10to50.size} users (10-50 ads), ${usersFor1to9.size} users (1-9 ads)")

        val usersWithAds = mutableListOf<Pair<User, IntRange>>()
        usersWithAds.addAll(usersFor50to1000.map { it to (50..1000) })
        usersWithAds.addAll(usersFor10to50.map { it to (10..50) })
        usersWithAds.addAll(usersFor1to9.map { it to (1..9) })

        val tasks = usersWithAds.map { (user, adCountRange) ->
            val numberOfAdsToCreate = Random.nextInt(adCountRange.first, adCountRange.last + 1)
            executorService.submit {
                createAdsForUser(user, numberOfAdsToCreate)
            }
        }

        tasks.forEach { it.get() }
        log.info("All ad population tasks completed.")
    }

    private fun createAdsForUser(user: User, count: Int, titlePrefix: String = "") {
        val userId = user.id?.toHexString()
        if (userId == null) {
            log.warn("User ${user.username} has no ID. Skipping ad creation.")
            return
        }

        log.debug("Creating $count ads for user ${user.username} on thread ${Thread.currentThread().name}")

        repeat(count) {
            try {
                val city = cityDataUnsorted.randomOrNull()
                if (city == null) {
                    log.error("Could not get a random city (city list might be empty). Skipping this ad creation.")
                    return@repeat
                }

                val template = adTemplates.random()
                val price = BigDecimal.valueOf(Random.nextDouble(MIN_PRICE, MAX_PRICE))
                    .setScale(2, RoundingMode.HALF_UP)
                val currency = if (Random.nextBoolean()) Currency.USD else Currency.ARRR
                val location = Location(city.id, doubleArrayOf(city.lon, city.lat))

                // 1. Create inactive ad
                val title = "$titlePrefix${template.title}"
                val createdAd = adService.createAd(userId, title)

                // --- Media Population Logic ---
                var adMediaPaths: List<String>? = null
                var adMainPhotoPath: String? = null

                if (Random.nextDouble() < MEDIA_ADD_CHANCE) {
                    val numberOfMediaItemsToAdd = Random.nextInt(1, MAX_MEDIA_ITEMS_PER_AD + 1)
                    val tempMediaPaths = mutableListOf<String>()
                    try {
                        repeat(numberOfMediaItemsToAdd) {
                            val mediaItem = playgroundMediaRepository.getRandomMediaItem()
                            tempMediaPaths.add(mediaItem.path)
                        }
                        if (tempMediaPaths.isNotEmpty()) {
                            adMediaPaths = tempMediaPaths.toList()
                            adMainPhotoPath = tempMediaPaths.random()
                        }
                    } catch (e: IllegalArgumentException) {
                        log.warn("Could not fetch random media items for ad for user ${user.username} (PlaygroundMediaRepository might be empty or an error occurred): ${e.message}")
                    } catch (e: Exception) {
                        log.error("Unexpected error fetching media items for ad for user ${user.username}: ${e.message}", e)
                    }
                }
                // --- End Media Population Logic ---

                // 2. Update ad with details (including media)
                val updateRequest = UpdateAdRequest(
                    title = title,
                    description = template.description,
                    price = CurrencyAmount(price, currency),
                    location = location,
                    mediaPaths = adMediaPaths,
                    mainPhotoPath = adMainPhotoPath,
                    stock = Random.nextInt(1, 100)
                )
                val updatedAd = adService.updateAd(createdAd.id, userId, updateRequest)

                // 3. Activate ad
                adService.activateAd(updatedAd.id, userId)

            } catch (e: AdService.AdOperationException) {
                log.warn("Skipping ad creation for user ${user.username} due to operation exception: ${e.message}")
            } catch (e: AdService.AdNotFoundException) {
                log.error("Error during ad creation sequence for user ${user.username}: Ad not found unexpectedly.", e)
            } catch (e: Exception) {
                log.error("Failed to create an ad for user ${user.username}", e)
            }
        }
        log.debug("Finished creating ads for user ${user.username}")
    }

    private fun shutdownAndAwaitTermination() {
        if (!::executorService.isInitialized || executorService.isShutdown) {
            return
        }

        log.info("Shutting down AdsPopulator executor service...")
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                log.error("Executor did not terminate in 60 seconds.")
                executorService.shutdownNow()
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate even after shutdownNow().")
                }
            }
            log.info("AdsPopulator executor service shut down successfully.")
        } catch (ie: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
            log.error("Shutdown was interrupted.", ie)
        }
    }
}