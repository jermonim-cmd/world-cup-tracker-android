# Real-Time Ticket Scanning Implementation

## Overview
This implementation adds comprehensive real-time ticket scanning capabilities to the World Cup Tracker app. Users can now monitor ticket prices continuously with customizable scan frequencies and receive instant notifications of price drops.

## Key Features

### 1. **Configurable Scan Frequencies**
Users can select from multiple scanning intervals:
- **Paused** — Stop scanning
- **30 seconds** — Ultra-frequent scanning (useful for active monitoring)
- **1 minute** — Default frequency (balanced approach)
- **5 minutes** — Moderate frequency
- **10 minutes** — Less frequent monitoring
- **30 minutes** — Light monitoring

Access via the scan frequency dropdown in the top-right corner of the app (next to the refresh button).

### 2. **Real-Time Price Change Detection**
The `TicketScanManager` maintains price history for each ticket listing and automatically detects when prices drop. When a price drop is detected:
- A `PriceUpdate` object is created with the old and new prices
- Notifications are triggered for the user
- Recent updates are displayed in the UI

### 3. **Smart Notifications**
The app provides two types of notifications:
- **Single Price Drop** — Shows detailed information about one price change
- **Multiple Price Drops** — Aggregates multiple updates into a single notification (shows top 5)

All notifications are high-priority and vibrate to grab user attention.

### 4. **Live Scan Status Display**
The UI shows:
- **Green dot (🟢)** when actively scanning
- **Black dot (⚫)** when paused
- Current scan frequency label
- Last updated timestamp

### 5. **Recent Price Updates Widget**
Recently detected price drops appear in a highlighted alert box at the top of the stadium list, showing:
- Number of price drops detected
- Match name and price change details
- Color-coded for easy visibility

## Architecture

### Core Components

#### `ScanFrequency` (Model)
Enum defining available scan frequencies with millisecond intervals.

#### `PriceUpdate` (Model)
Data class capturing:
- Stadium key
- Previous and new prices
- Match details
- Timestamp
- Boolean flags for price drops and change amounts

#### `TicketScanManager` (Singleton)
Manages the scanning flow:
- Creates a `Flow<ScanResult>` that emits scan results periodically
- Maintains price history across scans
- Detects price changes and new listings
- Returns both successful scans and errors

#### `TicketNotificationManager` (Singleton)
Handles notification delivery:
- Creates notification channels for Android 8.0+
- Formats and sends price drop alerts
- Handles both single and batch notifications

#### `DashboardViewModel`
Orchestrates the scanning system:
- Initializes scanning on app launch (default: 1-minute interval)
- Manages scan frequency changes
- Handles scan results and error states
- Maintains recent price updates in UI state
- Integrates with notification manager

#### `DashboardUiState`
Enhanced with:
- `scanFrequency` — Current scan interval
- `isScanning` — Whether actively scanning
- `recentPriceUpdates` — Last 10 price changes (for UI display)

### Data Flow

```
ViewModel.setScanFrequency() 
  ↓
TicketScanManager.createScanFlow(frequency)
  ↓
Periodic polling at specified interval
  ↓
Compare with price history
  ↓
Emit ScanResult (Success or Error)
  ↓
Update UI State + Show Notifications
```

## Usage

### For Users
1. Launch the app — scanning starts automatically at 1-minute intervals
2. Tap the scan frequency button (top-right) to change the interval
3. See recent price drops in the alert box at the top
4. Receive notifications for all detected price drops
5. Tap the refresh button anytime for an immediate manual scan

### For Developers

#### Change Default Scan Frequency
Edit `DashboardViewModel.kt`:
```kotlin
private var currentScanFrequency = ScanFrequency.FIVE_MINUTES  // Change this
```

#### Adjust Notification Behavior
Edit `TicketNotificationManager.kt` to customize:
- Notification channels
- Alert content and styling
- Vibration/sound patterns
- Batch vs. individual notifications

#### Add Price Drop Threshold
In `TicketScanManager.kt`, add filtering:
```kotlin
if (newPrice < previousPrice * 0.9) {  // Only alert on 10% drops
    updates.add(...)
}
```

#### Disable Notifications
In `DashboardViewModel.kt`, comment out:
```kotlin
// notificationManager.notifyPriceDrop(update)
```

## Files Added/Modified

### New Files
- `core/model/PriceUpdate.kt` — Price change tracking model
- `core/scanning/TicketScanManager.kt` — Scanning orchestration
- `core/notifications/NotificationManager.kt` — Notification delivery

### Modified Files
- `feature/dashboard/DashboardUiState.kt` — Added scan state
- `feature/dashboard/DashboardViewModel.kt` — Integrated scanning
- `feature/dashboard/DashboardScreen.kt` — Added UI controls
- `AndroidManifest.xml` — Added notification permission

## Permissions Required

The app now requires:
- `android.permission.INTERNET` (existing)
- `android.permission.POST_NOTIFICATIONS` (new, for Android 13+)

## Performance Considerations

- **Memory:** Price history is maintained in a `Map<String, Int>` — scales linearly with unique listings
- **Network:** Each scan performs one HTTP request to VividSeats
- **CPU:** Minimal — only parsing and comparison operations
- **Battery:** Configurable scan frequency allows users to balance timeliness vs. battery life

### Recommended Settings
- **Active monitoring:** 30 seconds
- **Normal use:** 1-5 minutes
- **Background monitoring:** 10-30 minutes

## Future Enhancements

1. **Persistent Price History** — Save history to local database
2. **Price Alerts** — Let users set custom price thresholds
3. **Background Service** — Continue scanning when app is backgrounded (WorkManager)
4. **Vibration Patterns** — Different patterns for different price changes
5. **Statistics** — Show price trends and historical data
6. **Multi-user** — Sync preferences across devices

## Testing Checklist

- [ ] App launches and scanning starts automatically
- [ ] Scan frequency dropdown shows all options
- [ ] Changing frequency updates the icon and label
- [ ] Manual refresh works during active scanning
- [ ] Price changes are detected and displayed
- [ ] Notifications appear when prices drop
- [ ] Recent updates widget shows correctly
- [ ] Green/black dot status indicator updates
- [ ] Pausing scan stops background polling
- [ ] Error states are handled gracefully
