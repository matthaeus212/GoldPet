package com.goldpet.domain.gold.dto

enum class IapPlatform {
    IOS,
    ANDROID
}

data class IapVerifyReceiptRequest(
    /** iOS: base64-encoded App Store receipt. Android: purchaseToken from Play Billing. */
    val platform: IapPlatform,
    val receiptData: String,
    /** Store product ID — must match gold_products.product_code */
    val productId: String
)

data class IapVerifyReceiptResponse(
    val verified: Boolean,
    /** NOT_IMPLEMENTED | SUCCESS | INVALID_RECEIPT | PRODUCT_NOT_FOUND */
    val status: String,
    val goldGranted: Int = 0,
    val message: String? = null
)
