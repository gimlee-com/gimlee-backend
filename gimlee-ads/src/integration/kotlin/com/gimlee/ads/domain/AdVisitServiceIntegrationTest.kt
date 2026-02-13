package com.gimlee.ads.domain

import com.gimlee.ads.persistence.AdVisitRepository
import com.gimlee.common.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import org.bson.types.ObjectId
import java.time.LocalDate

class AdVisitServiceIntegrationTest(
    private val adVisitService: AdVisitService,
    private val adVisitRepository: AdVisitRepository
) : BaseIntegrationTest({

    beforeSpec {
        adVisitRepository.clear()
    }

    Given("AdVisitService") {
        val adId = ObjectId().toHexString()
        val clientId = "client-1"
        val today = LocalDate.now()

        When("recording a visit for the first time") {
            adVisitService.recordVisit(adId, clientId, today)

            Then("it should increment the count to 1") {
                adVisitService.getVisitCount(adId, today, today) shouldBe 1
            }
        }

        When("recording a duplicate visit from the same client on the same day") {
            adVisitService.recordVisit(adId, clientId, today)
            adVisitService.recordVisit(adId, clientId, today)

            Then("it should NOT increment the count") {
                adVisitService.getVisitCount(adId, today, today) shouldBe 1
            }
        }

        When("recording a visit from a different client on the same day") {
            adVisitService.recordVisit(adId, "client-2", today)

            Then("it should increment the count to 2") {
                adVisitService.getVisitCount(adId, today, today) shouldBe 2
            }
        }

        When("recording a visit on a different day") {
            val yesterday = today.minusDays(1)
            adVisitService.recordVisit(adId, clientId, yesterday)

            Then("it should have counts for both days") {
                adVisitService.getVisitCount(adId, yesterday, yesterday) shouldBe 1
                adVisitService.getVisitCount(adId, today, today) shouldBe 2
                adVisitService.getVisitCount(adId, yesterday, today) shouldBe 3
            }
        }
        
        When("fetching daily visits") {
            val stats = adVisitService.getDailyVisits(adId, today.minusDays(7), today)
            
            Then("it should return the correct daily breakdown") {
                stats[today] shouldBe 2
                stats[today.minusDays(1)] shouldBe 1
            }
        }
    }
})
