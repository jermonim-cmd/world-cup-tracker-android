package com.worldcuptracker.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.app.NotificationCompat
import com.worldcuptracker.R
import com.worldcuptracker.core.model.PriceUpdate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val CHANNEL_ID = "ticket_alerts"
        private const val CHANNEL_NAME = "Ticket Price Alerts"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts for ticket price drops and new listings"
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun notifyPriceDrop(priceUpdate: PriceUpdate) {
        val change = priceUpdate.priceChange ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔥 Price Drop!")
            .setContentText(
                "${priceUpdate.match}: $${priceUpdate.previousPrice} → $${priceUpdate.newPrice} ($change)"
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${priceUpdate.match}\n" +
                    "${priceUpdate.date}\n" +
                    "${priceUpdate.stadiumKey.displayName}\n" +
                    "Dropped from $${priceUpdate.previousPrice} to $${priceUpdate.newPrice}"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NOTIFICATION_ID_BASE + priceUpdate.hashCode()
        manager.notify(notificationId, notification)
    }

    fun notifyMultiplePriceDrops(updates: List<PriceUpdate>) {
        if (updates.isEmpty()) return

        val inboxStyle = NotificationCompat.InboxStyle()
        updates.take(5).forEach { update ->
            inboxStyle.addLine("${update.match}: $${update.previousPrice} → $${update.newPrice}")
        }
        if (updates.size > 5) {
            inboxStyle.addLine("...and ${updates.size - 5} more")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🎟 ${updates.size} Price Drops Detected!")
            .setContentText("Tap to view details")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE, notification)
    }
}
