package com.worldcuptracker.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.worldcuptracker.core.data.TicketRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetUpdateWorkerEntryPoint {
        fun repository(): TicketRepository
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WidgetUpdateWorkerEntryPoint::class.java
        )
        val repository = entryPoint.repository()

        return try {
            val stadiums = repository.getTicketsByStadium()
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(QuickPriceWidget::class.java)

            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        stadiums.forEach { (key, data) ->
                            val minPrice = data.minPrice ?: 0
                            this[QuickPriceWidget.stadiumPriceKey(key)] = minPrice
                        }
                    }
                }
            }
            QuickPriceWidget().updateAll(context)
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                QuickPriceWidget.WIDGET_UPDATE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${QuickPriceWidget.WIDGET_UPDATE_WORK}_once",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
