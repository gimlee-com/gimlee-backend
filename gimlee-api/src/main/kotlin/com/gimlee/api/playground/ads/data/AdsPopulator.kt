package com.gimlee.api.playground.ads.data

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.CategoryService
import com.gimlee.ads.domain.model.CurrencyAmount
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.api.playground.ads.domain.AdTemplate
import com.gimlee.api.playground.media.data.PlaygroundMediaRepository
import com.gimlee.auth.domain.User
import com.gimlee.auth.persistence.UserRepository
import com.gimlee.common.domain.model.Currency
import com.gimlee.location.cities.data.cityDataUnsorted
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
    private val categoryService: CategoryService, // Inject CategoryService
    private val userRepository: UserRepository, // To get all users
    private val playgroundMediaRepository: PlaygroundMediaRepository // Inject PlaygroundMediaRepository
) {
    companion object {
        private val log = LogManager.getLogger()
        private const val ADS_TEMPLATE_RESOURCE_PATH = "/playground/ads.tsv"
        private const val MIN_PRICE = 1.0
        private const val MAX_REFERENCE_PRICE = 100.0
        private const val MAX_ARRR_PRICE = 400.0
        private const val MAX_YEC_PRICE = 500.0
        private const val ADS_CHECK_USER = "admin" // User to check if ads potentially exist
        private const val THREAD_POOL_SIZE = 8 // Dedicated thread pool size
        private const val MEDIA_ADD_CHANCE = 0.7 // 70% chance to add media to an ad
        private const val MAX_MEDIA_ITEMS_PER_AD = 20 // Max number of media items to add if chosen
        private const val MULTI_SETTLEMENT_CHANCE = 0.3 // 30% chance to accept all available settlement currencies
        private const val PLAYGROUND_SELLER_USERNAME = "playground_seller"
        private const val SELLER_ADS_PER_SETTLEMENT_CONFIG = 200
        private val REFERENCE_CURRENCIES = Currency.entries
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

        val playgroundSeller = userRepository.findOneByField("username", PLAYGROUND_SELLER_USERNAME)
        if (playgroundSeller != null) {
            val tasks = mutableListOf<java.util.concurrent.Future<*>>()
            val chunkSize = 50
            log.info("User '$PLAYGROUND_SELLER_USERNAME' found. Creating ads with ARRR-only, YEC-only, and ARRR+YEC settlement options.")
            repeat(SELLER_ADS_PER_SETTLEMENT_CONFIG / chunkSize) {
                tasks.add(executorService.submit {
                    createAdsForUser(playgroundSeller, chunkSize, "ARRR Ad ", forceSettlementCurrencies = setOf(Currency.ARRR))
                })
                tasks.add(executorService.submit {
                    createAdsForUser(playgroundSeller, chunkSize, "YEC Ad ", forceSettlementCurrencies = setOf(Currency.YEC))
                })
                tasks.add(executorService.submit {
                    createAdsForUser(playgroundSeller, chunkSize, "ARRR+YEC Ad ", forceSettlementCurrencies = setOf(Currency.ARRR, Currency.YEC))
                })
            }
            tasks.forEach { it.get() }
            log.info("Finished creating ads for '$PLAYGROUND_SELLER_USERNAME'.")
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

    private fun createAdsForUser(
        user: User,
        count: Int,
        titlePrefix: String = "",
        forceSettlementCurrency: Currency? = null,
        forceSettlementCurrencies: Set<Currency>? = null
    ) {
        val userId = user.id?.toHexString()
        if (userId == null) {
            log.warn("User ${user.username} has no ID. Skipping ad creation.")
            return
        }

        val allowedSettlementCurrencies = adService.getAllowedCurrencies(userId).toSet()
        if (allowedSettlementCurrencies.isEmpty()) {
            log.warn("User ${user.username} has no allowed settlement currencies. Skipping ad creation.")
            return
        }
        if (forceSettlementCurrency != null && forceSettlementCurrency !in allowedSettlementCurrencies) {
            log.warn("Forced settlement currency $forceSettlementCurrency is not allowed for user ${user.username}. Skipping ad creation.")
            return
        }
        if (forceSettlementCurrencies != null && (forceSettlementCurrencies.isEmpty() || !allowedSettlementCurrencies.containsAll(forceSettlementCurrencies))) {
            log.warn("Forced settlement currencies $forceSettlementCurrencies are not allowed for user ${user.username}. Skipping ad creation.")
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
                val pricingMode = if (Random.nextBoolean()) PricingMode.FIXED_CRYPTO else PricingMode.PEGGED
                val settlementCurrencies = resolveSettlementCurrencies(allowedSettlementCurrencies, forceSettlementCurrency, forceSettlementCurrencies)

                val (priceAmount, priceCurrency) = when (pricingMode) {
                    PricingMode.FIXED_CRYPTO -> {
                        val sc = settlementCurrencies.random()
                        randomPrice(sc) to sc
                    }
                    PricingMode.PEGGED -> {
                        val ref = REFERENCE_CURRENCIES.random()
                        randomPrice(ref) to ref
                    }
                }

                val volatilityProtection = pricingMode == PricingMode.PEGGED && Random.nextBoolean()
                val location = Location(city.id, doubleArrayOf(city.lon, city.lat))
                val randomCategoryId = categoryService.getRandomLeafCategoryId()

                // 1. Create inactive ad
                val title = "$titlePrefix${template.title}"
                val createdAd = adService.createAd(userId, title, randomCategoryId)

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
                    pricingMode = pricingMode,
                    price = CurrencyAmount(priceAmount, priceCurrency),
                    settlementCurrencies = settlementCurrencies,
                    location = location,
                    mediaPaths = adMediaPaths,
                    mainPhotoPath = adMainPhotoPath,
                    stock = Random.nextInt(1, 100),
                    volatilityProtection = volatilityProtection
                )
                val updatedAd = adService.updateAd(createdAd.id, userId, updateRequest)

                // 3. Activate ad
                adService.activateAd(updatedAd.id, userId)

            } catch (e: AdService.AdOperationException) {
                log.warn("Skipping ad creation for user ${user.username} due to operation exception: ${e.outcome}")
            } catch (e: AdService.AdNotFoundException) {
                log.error("Error during ad creation sequence for user ${user.username}: Ad not found unexpectedly.", e)
            } catch (e: Exception) {
                log.error("Failed to create an ad for user ${user.username}", e)
            }
        }
        log.debug("Finished creating ads for user ${user.username}")
    }

    private fun resolveSettlementCurrencies(
        allowedSettlementCurrencies: Set<Currency>,
        forced: Currency?,
        forcedSet: Set<Currency>?
    ): Set<Currency> {
        if (forcedSet != null) {
            return forcedSet
        }
        if (forced != null) {
            return if (Random.nextDouble() < MULTI_SETTLEMENT_CHANCE) allowedSettlementCurrencies else setOf(forced)
        }
        return if (Random.nextBoolean()) setOf(allowedSettlementCurrencies.random()) else allowedSettlementCurrencies
    }

    private fun randomPrice(currency: Currency): BigDecimal {
        val maxPrice = when (currency) {
            Currency.ARRR -> MAX_ARRR_PRICE
            Currency.YEC -> MAX_YEC_PRICE
            else -> MAX_REFERENCE_PRICE
        }
        return BigDecimal.valueOf(Random.nextDouble(MIN_PRICE, maxPrice))
            .setScale(currency.decimalPlaces, RoundingMode.HALF_UP)
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
