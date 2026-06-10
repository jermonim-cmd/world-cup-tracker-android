package com.worldcuptracker.core.model

data class LivePrice(
    val minPrice: Int,
    val maxPrice: Int,
    val averagePrice: Int,
    val listingCount: Int,
    val estimatedTotal: Int, // minPrice + estimated fees
    val estimatedFeePercent: Int = 15, // Vivid Seats fee percentage
    val serviceFeeAmount: Int = 0, // Extracted service fee
    val facilityFeeAmount: Int = 0, // Extracted facility fee
    val taxAmount: Int = 0, // Extracted tax
    val hasActualFees: Boolean = false, // Whether fees were parsed from page
    val currency: String = "USD", // Currency (USD or CAD)
)

enum class Currency(val code: String, val symbol: String) {
    USD("USD", "$"),
    CAD("CAD", "$"),
}