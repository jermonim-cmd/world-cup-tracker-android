package com.worldcuptracker.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.worldcuptracker.core.data.StadiumData
import com.worldcuptracker.core.model.LivePrice
import com.worldcuptracker.core.model.ScanFrequency
import com.worldcuptracker.core.model.StadiumKey
import com.worldcuptracker.core.model.TicketListing

// ── Color palette (matches the web dashboard) ──────────────────────────────
private val NavyDark    = Color(0xFF0F172A)
private val NavyMid     = Color(0xFF1E293B)
private val NavyHeader  = Color(0xFF0F2040)
private val NavyTopBar  = Color(0xFF1E3A5F)
private val Slate       = Color(0xFF334155)
private val SlateText   = Color(0xFF64748B)
private val TextPrimary = Color(0xFFE2E8F0)
private val TextMuted   = Color(0xFF94A3B8)
private val GreenPrice  = Color(0xFF4ADE80)
private val BlueAccent  = Color(0xFF60A5FA)
private val BlueButton  = Color(0xFF2563EB)

// ── Entry point ─────────────────────────────────────────────────────────────

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val livePriceState by viewModel.livePriceState.collectAsStateWithLifecycle()
    DashboardScreen(
        uiState = uiState,
        livePriceState = livePriceState,
        onRefresh = viewModel::refresh,
        onScanFrequencyChange = viewModel::setScanFrequency,
        onStadiumSelected = viewModel::selectStadium,
        onTeamSelected = viewModel::selectTeam,
        onListingClick = { url, match, minPrice, listingCount ->
            viewModel.fetchLivePrice(url, match, minPrice, listingCount)
        },
        onCloseLivePrice = viewModel::closeLivePriceDialog,
    )
}

// ── Root screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    livePriceState: LivePriceState,
    onRefresh: () -> Unit,
    onScanFrequencyChange: (ScanFrequency) -> Unit,
    onStadiumSelected: (StadiumKey?) -> Unit,
    onTeamSelected: (String?) -> Unit,
    onListingClick: (String, String, Int, Int) -> Unit,
    onCloseLivePrice: () -> Unit,
) {
    val showFrequencyMenu = remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "⚽ FIFA World Cup 2026",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        Text(
                            text = "Ticket Tracker · All Host Cities",
                            color = TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                },
                actions = {
                    if (uiState is DashboardUiState.Success) {
                        IconButton(
                            onClick = { showFrequencyMenu.value = true },
                        ) {
                            val statusDot = if (uiState.isScanning) "🟢" else "⚫"
                            Text(
                                text = "$statusDot ${uiState.scanFrequency.label}",
                                color = BlueButton,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        DropdownMenu(
                            expanded = showFrequencyMenu.value,
                            onDismissRequest = { showFrequencyMenu.value = false },
                            containerColor = NavyMid,
                        ) {
                            ScanFrequency.entries.forEach { frequency ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = frequency.label,
                                            color = if (frequency == uiState.scanFrequency) GreenPrice else TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        onScanFrequencyChange(frequency)
                                        showFrequencyMenu.value = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = uiState !is DashboardUiState.Loading,
                    ) {
                        if (uiState is DashboardUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = BlueButton,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh prices",
                                tint = BlueButton,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyTopBar),
            )
        },
    ) { padding ->
        when (uiState) {
            is DashboardUiState.Loading -> LoadingContent(Modifier.padding(padding))
            is DashboardUiState.Success -> {
                Column(modifier = Modifier.padding(padding)) {
                    FilterSection(
                        selectedStadium = uiState.selectedStadium,
                        selectedTeam = uiState.selectedTeam,
                        availableTeams = uiState.availableTeams,
                        onStadiumSelected = onStadiumSelected,
                        onTeamSelected = onTeamSelected,
                    )
                    SuccessContent(
                        stadiums = uiState.stadiums,
                        lastUpdated = uiState.lastUpdated,
                        recentUpdates = uiState.recentPriceUpdates,
                        onListingClick = onListingClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            is DashboardUiState.Error  -> ErrorContent(
                message = uiState.message,
                onRetry = onRefresh,
                modifier = Modifier.padding(padding),
            )
        }

        // Live price dialog
        when (livePriceState) {
            is LivePriceState.Success -> {
                LivePriceDialog(
                    match = livePriceState.match,
                    livePrice = livePriceState.price,
                    url = livePriceState.url,
                    isCached = livePriceState.isCached,
                    onDismiss = onCloseLivePrice,
                )
            }
            is LivePriceState.Loading -> {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Fetching live prices...", color = TextPrimary) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BlueButton)
                            Spacer(Modifier.height(12.dp))
                            Text(livePriceState.match, color = TextMuted, fontSize = 13.sp)
                        }
                    },
                    confirmButton = { },
                    containerColor = NavyMid,
                )
            }
            is LivePriceState.Error -> {
                AlertDialog(
                    onDismissRequest = onCloseLivePrice,
                    title = { Text("Could not load prices", color = TextPrimary) },
                    text = { Text(livePriceState.message, color = TextMuted) },
                    confirmButton = {
                        TextButton(onClick = onCloseLivePrice) {
                            Text("OK")
                        }
                    },
                    containerColor = NavyMid,
                )
            }
            else -> {}
        }
    }
}

// ── Filter Section ──────────────────────────────────────────────────────────

@Composable
private fun FilterSection(
    selectedStadium: StadiumKey?,
    selectedTeam: String?,
    availableTeams: List<String>,
    onStadiumSelected: (StadiumKey?) -> Unit,
    onTeamSelected: (String?) -> Unit,
) {
    val showStadiumMenu = remember { mutableStateOf(false) }
    val showTeamMenu = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyTopBar)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Stadium Selector
        Box(modifier = Modifier.weight(1f)) {
            Button(
                onClick = { showStadiumMenu.value = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyMid),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = selectedStadium?.displayName ?: "All Cities",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = showStadiumMenu.value,
                onDismissRequest = { showStadiumMenu.value = false },
                containerColor = NavyMid
            ) {
                DropdownMenuItem(
                    text = { Text("All Cities", color = TextPrimary) },
                    onClick = {
                        onStadiumSelected(null)
                        showStadiumMenu.value = false
                    }
                )
                StadiumKey.entries.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.displayName, color = TextPrimary) },
                        onClick = {
                            onStadiumSelected(key)
                            showStadiumMenu.value = false
                        }
                    )
                }
            }
        }

        // Team Selector
        Box(modifier = Modifier.weight(1f)) {
            Button(
                onClick = { showTeamMenu.value = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NavyMid),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = selectedTeam ?: "All Teams",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = showTeamMenu.value,
                onDismissRequest = { showTeamMenu.value = false },
                containerColor = NavyMid
            ) {
                DropdownMenuItem(
                    text = { Text("All Teams", color = TextPrimary) },
                    onClick = {
                        onTeamSelected(null)
                        showTeamMenu.value = false
                    }
                )
                availableTeams.forEach { team ->
                    DropdownMenuItem(
                        text = { Text(team, color = TextPrimary) },
                        onClick = {
                            onTeamSelected(team)
                            showTeamMenu.value = false
                        }
                    )
                }
            }
        }
    }
}

// ── State screens ───────────────────────────────────────────────────────────

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = BlueButton)
            Spacer(Modifier.height(16.dp))
            Text("Fetching ticket prices…", color = TextMuted)
        }
    }
}

@Composable
private fun SuccessContent(
    stadiums: Map<StadiumKey, StadiumData>,
    lastUpdated: String,
    recentUpdates: List<com.worldcuptracker.core.model.PriceUpdate> = emptyList(),
    onListingClick: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Last updated: $lastUpdated",
                color = SlateText,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (recentUpdates.isNotEmpty()) {
            item {
                PriceDropAlert(updates = recentUpdates.take(3))
            }
        }

        items(StadiumKey.entries, key = { it.name }) { key ->
            stadiums[key]?.let {
                StadiumCard(
                    it,
                    onListingClick = { url, match, minPrice, listingCount ->
                        onListingClick(url, match, minPrice, listingCount)
                    },
                )
            }
        }

        item {
            Text(
                text = "Secondary market prices · VividSeats",
                color = Slate,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Could not load prices",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = BlueButton),
            ) {
                Text("Try Again")
            }
        }
    }
}

// ── Stadium card ────────────────────────────────────────────────────────────

@Composable
private fun StadiumCard(
    data: StadiumData,
    onListingClick: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyMid),
    ) {
        Column {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column {
                    Text(
                        text = "🏟 ${data.key.displayName}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    Text(text = data.key.venue, color = SlateText, fontSize = 11.sp)
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    StatItem(
                        label = "Lowest price",
                        value = data.minPrice?.let { "\$${it.toFormattedPrice()}" } ?: "—",
                        valueColor = if (data.minPrice != null) GreenPrice else SlateText,
                        large = true,
                    )
                    StatItem(
                        label = "Listings",
                        value = data.listingCount.toString(),
                        valueColor = TextPrimary,
                    )
                }

                if (data.listings.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("No listings found", color = SlateText, fontSize = 13.sp)
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "CHEAPEST LISTINGS (${data.listings.size})",
                        color = SlateText,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    data.listings.forEach { listing ->
                        ListingRow(listing) { url, match, minPrice, listingCount ->
                            onListingClick(url, match, minPrice, listingCount)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ── Small components ────────────────────────────────────────────────────────

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color,
    large: Boolean = false,
) {
    Column {
        Text(text = label, color = SlateText, fontSize = 11.sp)
        Text(
            text = value,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (large) 22.sp else 16.sp,
        )
    }
}

@Composable
private fun ListingRow(listing: TicketListing, onClick: (String, String, Int, Int) -> Unit = { _, _, _, _ -> }) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(NavyDark)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(enabled = listing.url.isNotEmpty()) {
                onClick(listing.url, listing.match, listing.minPrice, listing.listingCount)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listing.match,
                color = TextPrimary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (listing.date.isNotBlank()) {
                    Text(text = listing.date, color = TextMuted, fontSize = 11.sp)
                }
                if (listing.listingCount > 0) {
                    Text(
                        text = "${listing.listingCount} listings",
                        color = SlateText,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "\$${listing.minPrice.toFormattedPrice()} CAD",
            color = BlueAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

// ── Price drop alert ────────────────────────────────────────────────────────

@Composable
private fun PriceDropAlert(updates: List<com.worldcuptracker.core.model.PriceUpdate>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F3A4D)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🔥 ${updates.size} Price Drop${if (updates.size > 1) "s" else ""}!",
                color = Color(0xFFFFA500),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            updates.forEach { update ->
                Text(
                    text = "${update.match}: \$${update.previousPrice} → \$${update.newPrice}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun Int.toFormattedPrice(): String = when {
    this >= 1_000 -> "%,d".format(this)
    else          -> toString()
}

// ── Live Price Dialog ───────────────────────────────────────────────────────

@Composable
private fun LivePriceDialog(
    match: String,
    livePrice: LivePrice,
    url: String,
    onDismiss: () -> Unit,
    isCached: Boolean = false,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ticket Prices (CAD)", color = TextPrimary, fontWeight = FontWeight.Bold)
                    if (isCached) {
                        Text(
                            "Cached",
                            color = TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(Color(0xFF1F3A4D), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(match, color = TextMuted, fontSize = 12.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PriceStatRow("Minimum", livePrice.minPrice, GreenPrice)
                PriceStatRow("Average", livePrice.averagePrice, BlueAccent)
                PriceStatRow("Maximum", livePrice.maxPrice, Color(0xFFFF6B6B))

                Spacer(Modifier.height(8.dp))

                // Fee Breakdown Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F3A4D)),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (livePrice.hasActualFees) "Actual Fees" else "Estimated Fees",
                            color = SlateText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(Modifier.height(8.dp))

                        // Price breakdown
                        PriceBreakdownRow("Ticket Price", livePrice.minPrice, TextPrimary)
                        if (livePrice.serviceFeeAmount > 0) {
                            PriceBreakdownRow("Service Fee", livePrice.serviceFeeAmount, SlateText)
                        }
                        if (livePrice.facilityFeeAmount > 0) {
                            PriceBreakdownRow("Facility Fee", livePrice.facilityFeeAmount, SlateText)
                        }
                        if (livePrice.taxAmount > 0) {
                            PriceBreakdownRow("Tax", livePrice.taxAmount, SlateText)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF334155))
                        )

                        Spacer(Modifier.height(8.dp))

                        // Total
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Estimated Total", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "$${livePrice.estimatedTotal.toFormattedPrice()}",
                                color = GreenPrice,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                        }

                        if (!livePrice.hasActualFees) {
                            Text(
                                "💡 Exact fees shown at Vivid Seats checkout",
                                color = TextMuted,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                Text(
                    "🔄 ${livePrice.listingCount} active listings available",
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                    onDismiss()
                },
            ) {
                Text("View on VividSeats")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = NavyMid,
    )
}

@Composable
private fun PriceStatRow(label: String, price: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = SlateText, fontSize = 12.sp)
        Text(
            "$${price.toFormattedPrice()}",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun PriceBreakdownRow(label: String, amount: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = color, fontSize = 11.sp)
        Text(
            "$${amount.toFormattedPrice()}",
            color = color,
            fontSize = 11.sp,
        )
    }
}
