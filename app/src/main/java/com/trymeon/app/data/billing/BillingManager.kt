package com.trymeon.app.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.trymeon.app.AppSettings
import com.trymeon.app.data.auth.CloudIdentity
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Product id you must create in Google Play Console → Monetize → In-app products. */
const val PRODUCT_AUDIT_UNLOCK = "audit_unlock"

/**
 * Thin BillingClient wrapper.
 * Falls back gracefully when Play Billing is unavailable — UI still reads
 * AppSettings.auditUnlocked so dev/test can flip it manually.
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private val settings = AppSettings(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _unlocked = MutableStateFlow(settings.auditUnlocked)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    /** Display price like "$2.99", or null if Play unavailable. */
    val formattedPrice: String? get() =
        _productDetails.value?.oneTimePurchaseOfferDetails?.formattedPrice

    fun start() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                _connected.value = result.responseCode == BillingClient.BillingResponseCode.OK
                if (_connected.value) {
                    scope.launch {
                        loadProduct()
                        restorePurchases()
                    }
                }
            }
            override fun onBillingServiceDisconnected() { _connected.value = false }
        })
    }

    fun stop() {
        runCatching { client.endConnection() }
    }

    private suspend fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_AUDIT_UNLOCK)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()
        val res = client.queryProductDetails(params)
        if (res.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _productDetails.value = res.productDetailsList?.firstOrNull()
        } else {
            Log.w(TAG, "queryProductDetails failed: ${res.billingResult.debugMessage}")
        }
    }

    private suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val res = client.queryPurchasesAsync(params)
        if (res.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            for (purchase in res.purchasesList) handlePurchase(purchase)
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = _productDetails.value ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return
        purchases?.forEach { handlePurchase(it) }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (PRODUCT_AUDIT_UNLOCK !in purchase.products) return

        // Local unlock is only a UI gate, so it can be optimistic.
        settings.auditUnlocked = true
        _unlocked.value = true

        if (!purchase.isAcknowledged) {
            scope.launch {
                val ack = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken).build()
                client.acknowledgePurchase(ack)
            }
        }

        // The relay's paid tier spends real money per call, so it is granted
        // only by the server after it checks the token against Play. Failure is
        // not fatal: restorePurchases() runs on every start and retries.
        scope.launch { grantServerEntitlement(purchase) }
    }

    private suspend fun grantServerEntitlement(purchase: Purchase) {
        val uid = CloudIdentity.uid()
        if (uid == null) {
            Log.d(TAG, "not signed in — deferring entitlement to next start")
            return
        }
        if (settings.entitlementSyncedFor == purchase.purchaseToken) return

        runCatching {
            FirebaseFunctions.getInstance()
                .getHttpsCallable("verifyPurchase")
                .call(
                    mapOf(
                        "productId" to PRODUCT_AUDIT_UNLOCK,
                        "purchaseToken" to purchase.purchaseToken
                    )
                )
                .await()
        }.onSuccess {
            settings.entitlementSyncedFor = purchase.purchaseToken
            Log.d(TAG, "server entitlement granted")
        }.onFailure {
            Log.w(TAG, "server entitlement not granted (will retry): ${it.message}")
        }
    }

    companion object { private const val TAG = "BillingManager" }
}
