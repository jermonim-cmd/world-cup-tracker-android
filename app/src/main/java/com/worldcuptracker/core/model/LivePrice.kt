package com.worldcuptracker.core.model

data class LivePrice(
    val minPrice: Int,
    val maxPrice: Int,
    val averagePrice: Int,
    val listingCount: Int,
    val estimatedTotal: Int, // minPrice + estimated fees
    val estimatedFeePercent: Int = 15, // Typical Vivid Seats fee percentage
)