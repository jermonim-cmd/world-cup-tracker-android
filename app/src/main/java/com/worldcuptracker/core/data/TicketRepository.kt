package com.worldcuptracker.core.data

import com.worldcuptracker.core.model.StadiumKey
import com.worldcuptracker.core.model.TicketListing
import com.worldcuptracker.core.network.VividSeatsDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class StadiumData(
    val key: StadiumKey,
    val listings: List<TicketListing>,
) {
    val minPrice: Int? get() = listings.minOfOrNull { it.minPrice }
    val listingCount: Int get() = listings.size
}

@Singleton
class TicketRepository @Inject constructor(
    private val vividSeats: VividSeatsDataSource,
) {
    /**
     * Returns ticket listings grouped by stadium, sorted cheapest first per stadium.
     */
    suspend fun getTicketsByStadium(): Map<StadiumKey, StadiumData> = withContext(Dispatchers.IO) {
        val allListings = vividSeats.fetchListings()

        // Group by stadiumKey (assigned by the data source from venue text)
        val grouped = allListings.groupBy { it.stadiumKey }

        StadiumKey.entries
            .filter { key -> grouped.containsKey(key) }
            .associateWith { key ->
                StadiumData(
                    key = key,
                    listings = (grouped[key] ?: emptyList()).sortedWith(
                        compareBy({ it.rawDate }, { it.minPrice })
                    ),
                )
            }
    }
}
