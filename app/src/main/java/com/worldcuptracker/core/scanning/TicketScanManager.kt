package com.worldcuptracker.core.scanning

import com.worldcuptracker.core.data.TicketRepository
import com.worldcuptracker.core.model.PriceUpdate
import com.worldcuptracker.core.model.ScanFrequency
import com.worldcuptracker.core.model.StadiumKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketScanManager @Inject constructor(
    private val repository: TicketRepository,
) {
    private val priceHistory = mutableMapOf<String, Int>()

    fun createScanFlow(scanFrequency: ScanFrequency): Flow<ScanResult> = flow {
        if (scanFrequency == ScanFrequency.PAUSED) return@flow

        while (true) {
            delay(scanFrequency.intervalMs)

            runCatching {
                val stadiums = repository.getTicketsByStadium()
                val updates = mutableListOf<PriceUpdate>()

                stadiums.forEach { (stadiumKey, stadiumData) ->
                    stadiumData.listings.forEach { listing ->
                        val key = "${stadiumKey.name}_${listing.match}"
                        val previousPrice = priceHistory[key]
                        priceHistory[key] = listing.minPrice

                        if (previousPrice != null && listing.minPrice < previousPrice) {
                            updates.add(
                                PriceUpdate(
                                    stadiumKey = stadiumKey,
                                    previousPrice = previousPrice,
                                    newPrice = listing.minPrice,
                                    match = listing.match,
                                    date = listing.date,
                                )
                            )
                        }
                    }
                }

                emit(ScanResult.Success(stadiums, updates))
            }.onFailure { error ->
                emit(ScanResult.Error(error.message ?: "Scan failed"))
            }
        }
    }

    fun clearHistory() {
        priceHistory.clear()
    }
}

sealed interface ScanResult {
    data class Success(
        val stadiums: Map<StadiumKey, com.worldcuptracker.core.data.StadiumData>,
        val priceUpdates: List<PriceUpdate>,
    ) : ScanResult

    data class Error(val message: String) : ScanResult
}