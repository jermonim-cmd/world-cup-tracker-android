package com.worldcuptracker.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.worldcuptracker.core.model.StadiumKey

class QuickPriceWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetContent(prefs)
        }
    }

    companion object {
        const val WIDGET_UPDATE_WORK = "quick_price_widget_update"

        fun stadiumPriceKey(stadium: StadiumKey) = intPreferencesKey("price_${stadium.name}")
    }
}

class QuickPriceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickPriceWidget()
}

@Composable
private fun WidgetContent(prefs: Preferences) {
    val navyMid = androidx.compose.ui.graphics.Color(0xFF1E293B)
    val textPrimary = androidx.compose.ui.graphics.Color(0xFFE2E8F0)
    val greenPrice = androidx.compose.ui.graphics.Color(0xFF4ADE80)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(navyMid))
            .cornerRadius(12.dp)
            .padding(12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚽ Quick Prices",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(textPrimary),
                    ),
                )
            }
            Spacer(GlanceModifier.height(8.dp))

            LazyColumn(
                modifier = GlanceModifier.fillMaxWidth(),
            ) {
                items(StadiumKey.entries) { stadium ->
                    val price = prefs[QuickPriceWidget.stadiumPriceKey(stadium)]
                    val priceText = if (price != null && price > 0) "$$price" else "--"

                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stadium.displayName,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = ColorProvider(textPrimary),
                            ),
                        )
                        Text(
                            priceText,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(greenPrice),
                            ),
                        )
                    }
                }
            }
        }
    }
}
