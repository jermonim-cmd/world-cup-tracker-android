package com.worldcuptracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = darkColorScheme(
    primary          = Color(0xFF2563EB),
    secondary        = Color(0xFF1E3A5F),
    tertiary         = Color(0xFF4ADE80),
    background       = Color(0xFF0F172A),
    surface          = Color(0xFF1E293B),
    onPrimary        = Color(0xFFFFFFFF),
    onBackground     = Color(0xFFE2E8F0),
    onSurface        = Color(0xFFE2E8F0),
)

@Composable
fun WorldCupTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content,
    )
}
