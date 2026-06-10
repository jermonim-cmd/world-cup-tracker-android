package com.worldcuptracker.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldcuptracker.core.data.StadiumData
import com.worldcuptracker.core.data.TicketRepository
import com.worldcuptracker.core.model.LivePrice
import com.worldcuptracker.core.model.ScanFrequency
import com.worldcuptracker.core.model.StadiumKey
import com.worldcuptracker.core.model.WorldCupTeams
import com.worldcuptracker.core.network.VividSeatsDataSource
import com.worldcuptracker.core.notifications.TicketNotificationManager
import com.worldcuptracker.core.scanning.ScanResult
import com.worldcuptracker.core.scanning.TicketScanManager
import com.worldcuptracker.feature.widget.WidgetUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TicketRepository,
    private val scanManager: TicketScanManager,
    private val notificationManager: TicketNotificationManager,
    private val vividSeatsDataSource: VividSeatsDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _livePriceState = MutableStateFlow<LivePriceState>(LivePriceState.Idle)
    val livePriceState: StateFlow<LivePriceState> = _livePriceState.asStateFlow()

    private var allStadiumData: Map<StadiumKey, StadiumData> = emptyMap()
    private var selectedStadium: StadiumKey? = null
    private var selectedTeam: String? = null

    private var scanJob: Job? = null
    private var currentScanFrequency = ScanFrequency.ONE_MINUTE

    init {
        loadPrices()
        startScanning()
    }

    fun refresh() {
        loadPrices()
    }

    fun selectStadium(stadium: StadiumKey?) {
        selectedStadium = stadium
        updateUiStateWithFilters()
    }

    fun selectTeam(team: String?) {
        selectedTeam = team
        updateUiStateWithFilters()
    }

    fun setScanFrequency(frequency: ScanFrequency) {
        currentScanFrequency = frequency
        scanJob?.cancel()
        if (frequency != ScanFrequency.PAUSED) {
            startScanning()
        } else {
            updateScanState(isScanning = false)
        }
    }

    private fun startScanning() {
        scanJob = viewModelScope.launch {
            scanManager.createScanFlow(currentScanFrequency).collect { result ->
                when (result) {
                    is ScanResult.Success -> {
                        allStadiumData = result.stadiums
                        WidgetUpdateWorker.runOnce(context)
                        val now = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                        
                        _uiState.value = DashboardUiState.Success(
                            stadiums = filterData(result.stadiums),
                            lastUpdated = now,
                            scanFrequency = currentScanFrequency,
                            isScanning = true,
                            recentPriceUpdates = emptyList(), // Simplified for now
                            selectedStadium = selectedStadium,
                            selectedTeam = selectedTeam,
                            availableTeams = extractUniqueTeams(result.stadiums),
                        )

                        if (result.priceUpdates.isNotEmpty()) {
                            notificationManager.notifyMultiplePriceDrops(result.priceUpdates)
                        }
                    }
                    is ScanResult.Error -> {
                        val currentState = _uiState.value as? DashboardUiState.Success
                        if (currentState != null) {
                            _uiState.value = currentState.copy(isScanning = false)
                        }
                    }
                }
            }
        }
    }

    private fun updateScanState(isScanning: Boolean) {
        val currentState = _uiState.value as? DashboardUiState.Success
        if (currentState != null) {
            _uiState.value = currentState.copy(isScanning = isScanning)
        }
    }

    private fun loadPrices() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            runCatching { repository.getTicketsByStadium() }
                .onSuccess { stadiums ->
                    allStadiumData = stadiums
                    val now = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                    _uiState.value = DashboardUiState.Success(
                        stadiums = filterData(stadiums),
                        lastUpdated = now,
                        scanFrequency = currentScanFrequency,
                        isScanning = true,
                        selectedStadium = selectedStadium,
                        selectedTeam = selectedTeam,
                        availableTeams = extractUniqueTeams(stadiums),
                    )
                }
                .onFailure { error ->
                    _uiState.value = DashboardUiState.Error(error.message ?: "Error")
                }
        }
    }

    private fun updateUiStateWithFilters() {
        val currentState = _uiState.value as? DashboardUiState.Success ?: return
        _uiState.value = currentState.copy(
            stadiums = filterData(allStadiumData),
            selectedStadium = selectedStadium,
            selectedTeam = selectedTeam,
        )
    }

    private fun filterData(data: Map<StadiumKey, StadiumData>): Map<StadiumKey, StadiumData> {
        var filtered = data
        if (selectedStadium != null) {
            filtered = filtered.filterKeys { it == selectedStadium }
        }
        if (selectedTeam != null) {
            filtered = filtered.mapValues { (_, stadiumData) ->
                stadiumData.copy(
                    listings = stadiumData.listings.filter { it.match.contains(selectedTeam!!, ignoreCase = true) }
                )
            }.filterValues { it.listings.isNotEmpty() }
        }
        return filtered
    }

    private fun extractUniqueTeams(data: Map<StadiumKey, StadiumData>): List<String> {
        val allMatchesText = data.values.flatMap { it.listings }.joinToString(" ") { it.match }

        // Only show countries that are actually in the match titles
        val discoveredTeams = WorldCupTeams.ALL.filter { team ->
            allMatchesText.contains(team, ignoreCase = true)
        }

        return discoveredTeams.ifEmpty { WorldCupTeams.ALL }.sorted()
    }

    fun fetchLivePrice(url: String, match: String, cachedMinPrice: Int = 0, cachedListingCount: Int = 0) {
        _livePriceState.value = LivePriceState.Loading(match)
        viewModelScope.launch {
            runCatching {
                vividSeatsDataSource.fetchLivePrice(url)
            }.onSuccess { livePrice ->
                if (livePrice != null) {
                    _livePriceState.value = LivePriceState.Success(match, livePrice, url, isCached = false)
                } else {
                    // If live fetch fails, show error encouraging user to open link
                    _livePriceState.value = LivePriceState.Error(
                        match,
                        "Could not fetch live prices from Vivid Seats. Opening app may show prices that differ from website if it hasn't refreshed recently. Try opening the link to see current prices.",
                        url
                    )
                }
            }.onFailure { error ->
                _livePriceState.value = LivePriceState.Error(
                    match,
                    "Network error: ${error.message ?: "Failed to fetch prices"}. Try opening the link directly on Vivid Seats.",
                    url
                )
            }
        }
    }

    fun closeLivePriceDialog() {
        _livePriceState.value = LivePriceState.Idle
    }
}

sealed class LivePriceState {
    object Idle : LivePriceState()
    data class Loading(val match: String) : LivePriceState()
    data class Success(val match: String, val price: LivePrice, val url: String, val isCached: Boolean = false) : LivePriceState()
    data class Error(val match: String, val message: String, val url: String) : LivePriceState()
}
