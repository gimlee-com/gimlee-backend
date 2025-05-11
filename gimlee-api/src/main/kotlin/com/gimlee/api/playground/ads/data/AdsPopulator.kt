package com.gimlee.api.playground.ads.data

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.ads.model.Currency
import com.gimlee.api.cities.data.cityDataUnsorted // Import the city data list
import com.gimlee.api.playground.ads.domain.AdTemplate
import com.gimlee.auth.domain.User // Assuming this is the correct User class
import com.gimlee.auth.persistence.UserRepository
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.logging.log4j.LogManager
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.io.InputStream
import java.math.BigDecimal
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
) {
    companion object {
        private val log = LogManager.getLogger()
        private const val ADS_TEMPLATE_RESOURCE_PATH = "/playground/ads.tsv"
        private const val MIN_PRICE = 10.0
        private const val MAX_PRICE = 5000.0
        private const val ADS_CHECK_USER = "admin" // User to check if ads potentially exist
        private const val THREAD_POOL_SIZE = 8 // Dedicated thread pool size
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
            executorService.submit { // Submit a task for each user needing ads
                val userId = user.id?.toHexString()
                if (userId == null) {
                     log.warn("User ${user.username} has no ID. Skipping ad creation.")
                     return@submit // Skip this task
                }

                val numberOfAds = Random.nextInt(adCountRange.first, adCountRange.last + 1)
                log.debug("Creating $numberOfAds ads for user ${user.username} on thread ${Thread.currentThread().name}")

                repeat(numberOfAds) {
                    try {
                        // --- City Assignment Fix ---
                        // Get a random city from the pre-loaded list
                        val city = cityDataUnsorted.randomOrNull()
                        if (city == null) {
                            log.error("Could not get a random city (city list might be empty). Skipping this ad creation.")
                            return@repeat // Skip this specific ad iteration
                        }
                        // --- End City Assignment Fix ---

                        val template = adTemplates.random() // Assuming adTemplates is not empty due to earlier check
                        val price = BigDecimal.valueOf(Random.nextDouble(MIN_PRICE, MAX_PRICE))
                            .setScale(2, BigDecimal.ROUND_HALF_UP) // Use RoundingMode import if needed
                        val currency = if (Random.nextBoolean()) Currency.USD else Currency.ARRR
                        val location = Location(city.id, doubleArrayOf(city.lon, city.lat)) // lon, lat order for GeoJsonPoint

                        // 1. Create inactive ad
                        val createdAd = adService.createAd(userId, template.title)

                        // 2. Update ad with details
                        val updateRequest = UpdateAdRequest(
                            title = template.title,
                            description = template.description,
                            price = price,
                            currency = currency,
                            location = location
                        )
                        val updatedAd = adService.updateAd(createdAd.id, userId, updateRequest)

                        // 3. Activate ad
                        adService.activateAd(updatedAd.id, userId)

                    } catch (e: AdService.AdOperationException) {
                        log.warn("Skipping ad creation for user ${user.username} due to operation exception: ${e.message}")
                    } catch (e: AdService.AdNotFoundException) {
                        log.error("Error during ad creation sequence for user ${user.username}: Ad not found unexpectedly.", e)
                    } catch (e: Exception) {
                        // Log general errors for this specific ad creation attempt
                        log.error("Failed to create an ad for user ${user.username}", e)
                    }
                    // Removed optional delay - use Thread.sleep() if strictly needed, but prefer non-blocking
                    // Thread.sleep(10) // Uncomment with caution - blocks the executor thread
                }
                log.debug("Finished creating ads for user ${user.username}")
            }
        }

        tasks.map { it.get() } // Wait for all submitted tasks to complete
        log.info("All ad population tasks completed.")
    }

    private fun shutdownAndAwaitTermination() {
        if (!::executorService.isInitialized || executorService.isShutdown) {
            return
        }

        log.info("Shutting down AdsPopulator executor service...")
        executorService.shutdown() // Disable new tasks from being submitted
        try {
            // Wait a reasonable time for existing tasks to terminate
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                log.error("Executor did not terminate in 60 seconds.")
                executorService.shutdownNow() // Cancel currently executing tasks
                // Wait a reasonable time for tasks to respond to being cancelled
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate even after shutdownNow().")
                }
            }
            log.info("AdsPopulator executor service shut down successfully.")
        } catch (ie: InterruptedException) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow()
            // Preserve interrupt status
            Thread.currentThread().interrupt()
            log.error("Shutdown was interrupted.", ie)
        }
    }
}