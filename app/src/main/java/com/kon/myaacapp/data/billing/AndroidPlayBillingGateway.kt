package com.kon.myaacapp.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class AndroidPlayBillingGateway(
    context: Context,
    private val currentActivityProvider: () -> Activity?,
) : PlayBillingGateway {

    private val appContext = context.applicationContext
    private val connectMutex = Mutex()
    private val productDetailsByOfferKey = ConcurrentHashMap<String, ProductDetails>()

    @Volatile
    private var purchasesUpdateListener:
        ((GatewayBillingResult, List<GatewayPurchase>) -> Unit)? = null

    private val sdkPurchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        val mappedResult = billingResult.toGatewayResult()
        val mappedPurchases = purchases.orEmpty().map { it.toGatewayPurchase() }
        purchasesUpdateListener?.invoke(mappedResult, mappedPurchases)
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(sdkPurchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    override fun setPurchasesUpdateListener(
        listener: (GatewayBillingResult, List<GatewayPurchase>) -> Unit,
    ) {
        purchasesUpdateListener = listener
    }

    override suspend fun ensureConnected(): GatewayBillingResult = connectMutex.withLock {
        if (billingClient.isReady) {
            return@withLock GatewayBillingResult(BillingClient.BillingResponseCode.OK)
        }

        var lastResult = GatewayBillingResult(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            lastResult = startSingleConnectionAttempt()
            if (lastResult.isOk) {
                return@withLock lastResult
            }
            if (!isTransientConnectionCode(lastResult.responseCode)) {
                return@withLock lastResult
            }
            if (attempt < maxAttempts) {
                delay(1_000L)
            }
        }
        lastResult
    }

    private suspend fun startSingleConnectionAttempt(): GatewayBillingResult =
        suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (continuation.isActive) {
                            continuation.resume(billingResult.toGatewayResult())
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        Log.w(TAG, "Play Billing service disconnected")
                        if (continuation.isActive) {
                            continuation.resume(
                                GatewayBillingResult(
                                    responseCode = BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                                    debugMessage = "Play Billing service disconnected during setup",
                                ),
                            )
                        }
                    }
                },
            )
        }

    override suspend fun querySubscriptionProduct(
        productId: String,
        preferredBasePlanId: String,
    ): Pair<GatewayBillingResult, GatewayProductOffer?> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient.queryProductDetailsAsync(params) { billingResult, result ->
                val gatewayResult = billingResult.toGatewayResult()
                if (!gatewayResult.isOk) {
                    if (continuation.isActive) {
                        continuation.resume(gatewayResult to null)
                    }
                    return@queryProductDetailsAsync
                }

                val productDetails = result.productDetailsList
                    .firstOrNull { it.productId == productId }

                if (productDetails == null) {
                    Log.w(
                        TAG,
                        "Subscription product '$productId' not found; unfetched=${result.unfetchedProductList.size}",
                    )
                    if (continuation.isActive) {
                        continuation.resume(
                            GatewayBillingResult(
                                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                                debugMessage = "Product $productId unavailable",
                            ) to null,
                        )
                    }
                    return@queryProductDetailsAsync
                }

                val selectedOffer = selectBestOffer(
                    productDetails = productDetails,
                    preferredBasePlanId = preferredBasePlanId,
                )

                if (selectedOffer != null) {
                    productDetailsByOfferKey[offerCacheKey(selectedOffer.productId, selectedOffer.offerToken)] =
                        productDetails
                }

                if (continuation.isActive) {
                    continuation.resume(gatewayResult to selectedOffer)
                }
            }
        }
    }

    override suspend fun querySubscriptionPurchases(): Pair<GatewayBillingResult, List<GatewayPurchase>> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (continuation.isActive) {
                    continuation.resume(
                        billingResult.toGatewayResult() to purchases.map { it.toGatewayPurchase() },
                    )
                }
            }
        }
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): GatewayBillingResult {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient.acknowledgePurchase(params) { billingResult ->
                if (continuation.isActive) {
                    continuation.resume(billingResult.toGatewayResult())
                }
            }
        }
    }

    override suspend fun launchSubscriptionPurchaseFlow(
        offer: GatewayProductOffer,
    ): GatewayBillingResult = withContext(Dispatchers.Main.immediate) {
        val activity = currentActivityProvider()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            return@withContext GatewayBillingResult(
                responseCode = BillingClient.BillingResponseCode.ERROR,
                debugMessage = "No active foreground Activity available for purchase flow",
            )
        }

        val productDetails = productDetailsByOfferKey[offerCacheKey(offer.productId, offer.offerToken)]
            ?: return@withContext GatewayBillingResult(
                responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                debugMessage = "ProductDetails missing for ${offer.productId}",
            )

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offer.offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams).toGatewayResult()
    }

    override fun endConnection() {
        runCatching { billingClient.endConnection() }
    }

    private fun selectBestOffer(
        productDetails: ProductDetails,
        preferredBasePlanId: String,
    ): GatewayProductOffer? {
        val offers = productDetails.subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) {
            return null
        }

        val candidates = if (preferredBasePlanId.isNotBlank()) {
            offers.filter { it.basePlanId == preferredBasePlanId }.ifEmpty { offers }
        } else {
            offers
        }

        val chosen = candidates.firstOrNull { it.offerId == null } ?: candidates.first()
        val phases = chosen.pricingPhases.pricingPhaseList
        if (phases.isEmpty()) {
            return null
        }

        val recurringPhase = phases.lastOrNull { it.priceAmountMicros > 0L } ?: phases.last()
        val trialPhase = phases.firstOrNull { it.priceAmountMicros == 0L }

        return GatewayProductOffer(
            productId = productDetails.productId,
            basePlanId = chosen.basePlanId,
            offerToken = chosen.offerToken,
            formattedPrice = recurringPhase.formattedPrice,
            billingPeriodIso8601 = recurringPhase.billingPeriod,
            freeTrialPeriodIso8601 = trialPhase?.billingPeriod,
        )
    }

    private fun BillingResult.toGatewayResult(): GatewayBillingResult {
        return GatewayBillingResult(
            responseCode = responseCode,
            subResponseCode = onPurchasesUpdatedSubResponseCode,
            debugMessage = debugMessage,
        )
    }

    private fun Purchase.toGatewayPurchase(): GatewayPurchase {
        val mappedState = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> GatewayPurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> GatewayPurchaseState.PENDING
            else -> GatewayPurchaseState.UNSPECIFIED
        }
        return GatewayPurchase(
            orderId = orderId,
            packageName = packageName,
            products = products,
            purchaseToken = purchaseToken,
            purchaseState = mappedState,
            isAcknowledged = isAcknowledged,
            isAutoRenewing = isAutoRenewing,
        )
    }

    private fun offerCacheKey(productId: String, offerToken: String): String =
        "$productId::$offerToken"

    private fun isTransientConnectionCode(responseCode: Int): Boolean {
        return responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ||
            responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ||
            responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR ||
            responseCode == BillingClient.BillingResponseCode.ERROR
    }

    private companion object {
        const val TAG = "AndroidPlayBillingGw"
    }
}
