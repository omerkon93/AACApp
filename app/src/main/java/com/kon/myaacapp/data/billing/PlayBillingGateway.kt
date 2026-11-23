package com.kon.myaacapp.data.billing

import com.android.billingclient.api.BillingClient

enum class GatewayPurchaseState {
    PURCHASED,
    PENDING,
    UNSPECIFIED,
}

data class GatewayBillingResult(
    val responseCode: Int,
    val subResponseCode: Int = BillingClient.OnPurchasesUpdatedSubResponseCode.NO_APPLICABLE_SUB_RESPONSE_CODE,
    val debugMessage: String = "",
) {
    val isOk: Boolean
        get() = responseCode == BillingClient.BillingResponseCode.OK
}

data class GatewayProductOffer(
    val productId: String,
    val basePlanId: String,
    val offerToken: String,
    val formattedPrice: String,
    val billingPeriodIso8601: String,
    val freeTrialPeriodIso8601: String? = null,
)

data class GatewayPurchase(
    val orderId: String?,
    val packageName: String,
    val products: List<String>,
    val purchaseToken: String,
    val purchaseState: GatewayPurchaseState,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
)

interface PlayBillingGateway {
    fun setPurchasesUpdateListener(
        listener: (GatewayBillingResult, List<GatewayPurchase>) -> Unit,
    )

    suspend fun ensureConnected(): GatewayBillingResult

    suspend fun querySubscriptionProduct(
        productId: String,
        preferredBasePlanId: String = "",
    ): Pair<GatewayBillingResult, GatewayProductOffer?>

    suspend fun querySubscriptionPurchases(): Pair<GatewayBillingResult, List<GatewayPurchase>>

    suspend fun acknowledgePurchase(purchaseToken: String): GatewayBillingResult

    suspend fun launchSubscriptionPurchaseFlow(offer: GatewayProductOffer): GatewayBillingResult

    fun endConnection()
}
