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
    val currency: String = "CAD", // Always CAD
    val originalCurrency: String = "USD", // Original currency before conversion
)

object CurrencyConverter {
    // USD to CAD exchange rate (typically 1 USD = 1.36 CAD)
    private const val USD_TO_CAD_RATE = 1.36

    fun usdToCad(priceUsd: Int): Int {
        return (priceUsd * USD_TO_CAD_RATE).toInt()
    }

    fun usdToCadDouble(priceUsd: Double): Double {
        return priceUsd * USD_TO_CAD_RATE
    }
}