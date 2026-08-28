package com.craftengine.diamondcraft

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.android.billingclient.api.UnfetchedProduct

/** Google Play Billing entitlement for the one-time DiamondCraft Pro purchase. */
class PlayBillingController(context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRO_PRODUCT_ID = "diamondcraft_pro_lifetime"
    }

    var isPro by mutableStateOf(false)
        private set

    var isReady by mutableStateOf(false)
        private set

    var status by mutableStateOf("Подключение к Google Play…")
        private set

    private var productDetails: ProductDetails? = null
    private var purchaseActivity: Activity? = null
    private var purchaseRequested = false

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        connect()
    }

    private fun connect() {
        if (billingClient.isReady) {
            isReady = true
            queryProduct()
            refresh()
            return
        }
        status = "Подключение к Google Play…"
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isReady = true
                    status = "Google Play подключён. Получаем товар…"
                    queryProduct()
                    refresh()
                } else {
                    isReady = false
                    status = billingError("Google Play Billing недоступен", result)
                }
            }

            override fun onBillingServiceDisconnected() {
                isReady = false
                status = "Связь с Google Play временно потеряна. Нажмите «Получить Pro» ещё раз."
            }
        })
    }

    private fun queryProduct() {
        if (!billingClient.isReady) {
            connect()
            return
        }

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                productDetails = null
                status = billingError("Не удалось получить DiamondCraft Pro", result)
                clearPendingPurchase()
                return@queryProductDetailsAsync
            }

            val details = detailsResult.productDetailsList.firstOrNull { it.productId == PRO_PRODUCT_ID }
            val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                ?: details?.oneTimePurchaseOfferDetails

            if (details == null) {
                productDetails = null
                val unfetched = detailsResult.unfetchedProductList.firstOrNull { it.productId == PRO_PRODUCT_ID }
                status = unfetchedStatus(unfetched)
                clearPendingPurchase()
                return@queryProductDetailsAsync
            }

            if (offer == null || offer.offerToken.isBlank()) {
                productDetails = null
                status = "Товар найден, но Google Play не вернул доступный способ покупки. Проверьте активность buy-pro-lifetime и страны тестового аккаунта."
                clearPendingPurchase()
                return@queryProductDetailsAsync
            }

            productDetails = details
            status = "DiamondCraft Pro доступен: ${offer.formattedPrice}"

            if (purchaseRequested) {
                val activity = purchaseActivity
                purchaseRequested = false
                purchaseActivity = null
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    activity.runOnUiThread { launchWithDetails(activity, details, offer.offerToken) }
                } else {
                    status = "Не удалось открыть окно покупки: экран приложения уже закрыт."
                }
            }
        }
    }

    /** Always refreshes ProductDetails before launching so a newly activated Play product is picked up. */
    fun launchPurchase(activity: Activity) {
        purchaseActivity = activity
        purchaseRequested = true
        status = "Получаем актуальную цену Google Play…"
        if (!billingClient.isReady) connect() else queryProduct()
    }

    private fun launchWithDetails(activity: Activity, details: ProductDetails, offerToken: String) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            status = billingError("Не удалось открыть покупку", result)
        }
    }

    fun refresh() {
        if (!billingClient.isReady) {
            connect()
            return
        }
        status = if (productDetails == null) "Проверяем покупки и товар…" else status
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
                if (productDetails == null) queryProduct()
            } else {
                status = billingError("Не удалось восстановить покупки", result)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> status = "Покупка отменена"
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> status = billingError("Ошибка Google Play Billing", result)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val proPurchase = purchases.firstOrNull { purchase ->
            PRO_PRODUCT_ID in purchase.products &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        isPro = proPurchase != null
        if (proPurchase == null) {
            if (productDetails != null) {
                val offer = productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                    ?: productDetails?.oneTimePurchaseOfferDetails
                status = if (offer != null) "DiamondCraft Pro доступен: ${offer.formattedPrice}" else "Бесплатный режим"
            }
            return
        }

        status = "DiamondCraft Pro активирован"
        if (!proPurchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(proPurchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    status = billingError("Pro активирован; подтверждение покупки будет повторено", result)
                }
            }
        }
    }

    private fun clearPendingPurchase() {
        purchaseRequested = false
        purchaseActivity = null
    }

    private fun unfetchedStatus(unfetched: UnfetchedProduct?): String {
        if (unfetched == null) {
            return "Google Play не вернул товар $PRO_PRODUCT_ID. Проверьте тестовый аккаунт и публикацию сборки."
        }
        return when (unfetched.statusCode) {
            UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND ->
                "Google Play не видит товар $PRO_PRODUCT_ID (PRODUCT_NOT_FOUND)."
            UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER ->
                "Товар найден, но для этого аккаунта/страны нет доступного предложения (NO_ELIGIBLE_OFFER)."
            UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT ->
                "Google Play отклонил ID товара $PRO_PRODUCT_ID (INVALID_PRODUCT_ID_FORMAT)."
            else -> "Google Play не смог получить товар $PRO_PRODUCT_ID (код ${unfetched.statusCode})."
        }
    }

    private fun billingError(prefix: String, result: BillingResult): String {
        val message = result.debugMessage.ifBlank { "без описания" }
        return "$prefix: код ${result.responseCode}, $message"
    }
}
