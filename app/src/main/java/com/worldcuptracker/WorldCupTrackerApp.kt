package com.worldcuptracker

import android.app.Application
import com.worldcuptracker.feature.widget.WidgetUpdateWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WorldCupTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetUpdateWorker.enqueue(this)
    }
}
