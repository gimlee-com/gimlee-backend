package com.gimlee.api.web

import com.gimlee.ads.domain.AdService
import com.gimlee.ads.domain.model.Location
import com.gimlee.ads.domain.model.UpdateAdRequest
import com.gimlee.api.web.dto.PurchaseDetailDto
import com.gimlee.api.web.dto.PurchaseResponseDto
import com.gimlee.api.web.dto.SalesOrderDetailDto
import com.gimlee.auth.persistence.UserRoleRepository
import com.gimlee.auth.model.Role
import com.gimlee.common.BaseIntegrationTest
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.toMicros
import com.gimlee.payments.crypto.persistence.IncomingTransactionRepository
import com.gimlee.payments.crypto.persistence.UserWalletAddressRepository
import com.gimlee.payments.crypto.persistence.model.IncomingTransactionDocument
import com.gimlee.payments.crypto.persistence.model.WalletAddressInfo
import com.gimlee.payments.crypto.persistence.model.WalletShieldedAddressType
import com.gimlee.purchases.web.dto.request.PurchaseItemRequestDto
import com.gimlee.purchases.web.dto.request.PurchaseRequestDto
import com.gimlee.user.domain.DeliveryAddressService
import com.gimlee.user.domain.UserPreferencesService
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

class OrderTransactionsIntegrationTest(
    private val adService: AdService,
    private val userRoleRepository: UserRoleRepository,
    private val userWalletAddressRepository: UserWalletAddressRepository,
    private val deliveryAddressService: DeliveryAddressService,
    private val userPreferencesService: UserPreferencesService,
    private val incomingTransactionRepository: IncomingTransactionRepository
) : BaseIntegrationTest({

    fun setupUser(username: String, vararg roles: Role): ObjectId {
        val userId = ObjectId.get()
        roles.forEach { userRoleRepository.add(userId, it) }
        return userId
    }

    fun authHeaders(userId: ObjectId, username: String, vararg roles: String): Map<String, String> {
        return restClient.createAuthHeader(
            subject = userId.toHexString(),
            username = username,
            roles = roles.toList()
        )
    }

    Given("a seller with an ad and a buyer") {
        val sellerId = setupUser("seller", Role.USER, Role.PIRATE)
        val buyerId = setupUser("buyer", Role.USER)

        // Setup seller wallet
        val zAddress = "zs1testaddress"
        val addressInfo = WalletAddressInfo(
            type = Currency.ARRR,
            addressType = WalletShieldedAddressType.Z_SAPLING,
            zAddress = zAddress,
            viewKeyHash = "hash",
            viewKeySalt = "salt",
            lastUpdateTimestamp = Instant.now().toMicros()
        )
        userWalletAddressRepository.addAddressToUser(sellerId, addressInfo)

        // Setup ad
        val ad = adService.createAd(sellerId.toHexString(), "Test Item", null, 10)
        adService.updateAd(ad.id, sellerId.toHexString(), UpdateAdRequest(
            fixedPrices = mapOf(Currency.ARRR to BigDecimal("10.00")),
            location = Location("Warsaw", doubleArrayOf(52.2297, 21.0122)),
            stock = 10
        ))
        adService.activateAd(ad.id, sellerId.toHexString())

        // Setup buyer preferences and address
        userPreferencesService.updateUserPreferences(buyerId.toHexString(), "en-US", "ARRR", "PL")
        val buyerAddress = deliveryAddressService.addDeliveryAddress(
            userId = buyerId.toHexString(),
            name = "Home",
            fullName = "Test Buyer",
            street = "123 Main St",
            city = "Warsaw",
            postalCode = "00-001",
            country = "PL",
            phoneNumber = "+48123456789",
            isDefault = true
        )

        When("the buyer makes a purchase") {
            val purchaseRequest = PurchaseRequestDto(
                items = listOf(PurchaseItemRequestDto(adId = ad.id, quantity = 1, unitPrice = BigDecimal("10.00"))),
                currency = Currency.ARRR,
                deliveryAddressId = buyerAddress.id
            )
            val buyerHeaders = authHeaders(buyerId, "buyer", "USER")
            val purchaseResponse = restClient.post("/purchases", purchaseRequest, buyerHeaders)
                .bodyAs<PurchaseResponseDto>()!!

            val purchaseId = purchaseResponse.purchaseId
            val memo = purchaseResponse.payment!!.memo

            And("a cryptocurrency transaction is detected for this purchase") {
                val txid = "tx123456789"
                val transaction = IncomingTransactionDocument(
                    type = Currency.ARRR,
                    userId = sellerId,
                    address = zAddress,
                    txid = txid,
                    memo = memo,
                    amount = 10.0,
                    confirmations = 3,
                    detectedAtMicros = Instant.now().toMicros()
                )
                incomingTransactionRepository.save(transaction)

                Then("the seller should see the transaction in order details") {
                    val sellerHeaders = authHeaders(sellerId, "seller", "USER", "PIRATE")
                    val orderDetailsResponse = restClient.get("/sales/orders/$purchaseId", sellerHeaders)
                    
                    orderDetailsResponse.statusCode shouldBe 200
                    val details = orderDetailsResponse.bodyAs<SalesOrderDetailDto>()!!
                    details.cryptoTransactions shouldHaveSize 1
                    val tx = details.cryptoTransactions.first()
                    tx.txid shouldBe txid
                    tx.memo shouldBe memo
                    tx.amount shouldBe 10.0
                    tx.currency shouldBe "ARRR"
                    tx.address shouldBe zAddress
                }

                Then("the buyer should see the transaction in purchase details") {
                    val purchaseDetailsResponse = restClient.get("/purchases/$purchaseId", buyerHeaders)
                    
                    purchaseDetailsResponse.statusCode shouldBe 200
                    val details = purchaseDetailsResponse.bodyAs<PurchaseDetailDto>()!!
                    details.cryptoTransactions shouldHaveSize 1
                    val tx = details.cryptoTransactions.first()
                    tx.txid shouldBe txid
                    tx.memo shouldBe memo
                    tx.amount shouldBe 10.0
                    tx.currency shouldBe "ARRR"
                    tx.address shouldBe zAddress
                }
            }
        }
    }
})
