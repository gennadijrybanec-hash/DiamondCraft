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

/**
 * Google Play Billing entitlement for the one-time DiamondCraft Pro purchase.
 *
 * Debug APKs deliberately don't depend on this class for entitlement so device testing
 * stays fully unlocked. Release builds use the verified Play purchase state.
 */
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

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        connect()
    }

    private fun connect() {
        if (billingClient.isReady) {
            isReady = true
            refresh()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    isReady = true
                    status = "Google Play подключён"
                    queryProduct()
                    refresh()
                } else {
                    status = "Google Play Billing недоступен: ${result.debugMessage}"
                }
            }

            override fun onBillingServiceDisconnected() {
                isReady = false
                status = "Связь с Google Play временно потеряна"
            }
        })
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = detailsResult.productDetailsList.firstOrNull()
                if (productDetails == null && !isPro) {
                    status = "Pro станет доступен после настройки товара в Google Play"
                }
            } else {
                status = "Не удалось получить данные DiamondCraft Pro"
            }
        }
    }

    fun refresh() {
        if (!billingClient.isReady) {
            connect()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            } else {
                status = "Не удалось восстановить покупки"
            }
        }
    }

    fun launchPurchase(activity: Activity): Boolean {
        val details = productDetails
        if (!billingClient.isReady || details == null) {
            status = "Покупка пока недоступна. Проверьте Google Play и повторите."
            if (!billingClient.isReady) connect() else queryProduct()
            return false
        }

        val offerToken = details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (!offerToken.isNullOrBlank()) {
            productParamsBuilder.setOfferToken(offerToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            status = result.debugMessage.ifBlank { "Не удалось открыть покупку" }
            return false
        }
        return true
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> status = "Покупка отменена"
            else -> status = result.debugMessage.ifBlank { "Ошибка Google Play Billing" }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val proPurchase = purchases.firstOrNull { purchase ->
            PRO_PRODUCT_ID in purchase.products &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        isPro = proPurchase != null
        if (proPurchase == null) {
            status = "Бесплатный режим"
            return
        }

        status = "DiamondCraft Pro активирован"
        if (!proPurchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(proPurchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    status = "Pro активирован; подтверждение покупки будет повторено"
                }
            }
        }
    }
}
