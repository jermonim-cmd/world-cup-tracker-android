package com.worldcuptracker.feature.dashboard

import com.worldcuptracker.core.data.StadiumData
import com.worldcuptracker.core.model.PriceUpdate
import com.worldcuptracker.core.model.ScanFrequency
import com.worldcuptracker.core.model.StadiumKey

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Success(
        val stadiums: Map<StadiumKey, StadiumData>,
        val lastUpdated: String,
        val scanFrequency: ScanFrequency = ScanFrequency.ONE_MINUTE,
        val isScanning: Boolean = false,
        val recentPriceUpdates: List<PriceUpdate> = emptyList(),
        val selectedStadium: StadiumKey? = null,
        val selectedTeam: String? = null,
        val availableTeams: List<String> = emptyList(),
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}
