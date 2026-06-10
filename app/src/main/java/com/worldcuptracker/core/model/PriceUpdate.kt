package com.worldcuptracker.core.model

data class PriceUpdate(
    val stadiumKey: StadiumKey,
    val previousPrice: Int?,
    val newPrice: Int,
    val match: String,
    val date: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val isPriceDrop: Boolean = previousPrice != null && newPrice < previousPrice
    val priceChange: Int? = if (previousPrice != null) newPrice - previousPrice else null
}

enum class ScanFrequency(val intervalMs: Long, val label: String) {
    PAUSED(0L, "Paused"),
    THIRTY_SECONDS(30_000L, "30s"),
    ONE_MINUTE(60_000L, "1 min"),
    FIVE_MINUTES(300_000L, "5 min"),
    TEN_MINUTES(600_000L, "10 min"),
    THIRTY_MINUTES(1_800_000L, "30 min"),
}