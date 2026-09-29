package com.neochildclinic.feature.widget.presentation

import android.content.Context
import androidx.compose.ui.graphics.Color

data class WidgetColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color
)

enum class VaccineWidgetTheme(
    val key: String,
    val label: String,
    val colorsProvider: (Context) -> WidgetColors
) {
    SYSTEM("system", "System", { _ ->
        WidgetColors(
            background = Color(0xFFFFFFFF),
            primaryText = Color(0xFF1E3A5F),
            secondaryText = Color(0xFF64748B),
            accent = Color(0xFF1976D2)
        )
    }),
    LIGHT("light", "Light", { _ ->
        WidgetColors(
            background = Color(0xFFFFFFFF),
            primaryText = Color(0xFF1E3A5F),
            secondaryText = Color(0xFF64748B),
            accent = Color(0xFF1976D2)
        )
    }),
    DARK("dark", "Dark", { _ ->
        WidgetColors(
            background = Color(0xFF1A1C1E),
            primaryText = Color(0xFFE2E2E6),
            secondaryText = Color(0xFF94A3B8),
            accent = Color(0xFF90CAF9)
        )
    }),
    MIDNIGHT("midnight", "Midnight", { _ ->
        WidgetColors(
            background = Color(0xFF0F172A),
            primaryText = Color(0xFFF8FAFC),
            secondaryText = Color(0xFF94A3B8),
            accent = Color(0xFF38BDF8)
        )
    }),
    GLASS("glass", "Glass", { _ ->
        WidgetColors(
            background = Color(0xCCFFFFFF),
            primaryText = Color(0xFF0F172A),
            secondaryText = Color(0xFF475569),
            accent = Color(0xFF0284C7)
        )
    }),
    CLINIC_BLUE("clinic_blue", "Clinic Blue", { _ ->
        WidgetColors(
            background = Color(0xFFE3F2FD),
            primaryText = Color(0xFF0D47A1),
            secondaryText = Color(0xFF1976D2),
            accent = Color(0xFF1565C0)
        )
    }),
    CLINIC_GREEN("clinic_green", "Clinic Green", { _ ->
        WidgetColors(
            background = Color(0xFFE8F5E9),
            primaryText = Color(0xFF1B5E20),
            secondaryText = Color(0xFF388E3C),
            accent = Color(0xFF2E7D32)
        )
    }),
    WARM("warm", "Warm", { _ ->
        WidgetColors(
            background = Color(0xFFFFFBEB),
            primaryText = Color(0xFF78350F),
            secondaryText = Color(0xFFB45309),
            accent = Color(0xFFD97706)
        )
    }),
    HIGH_CONTRAST("high_contrast", "High Contrast", { _ ->
        WidgetColors(
            background = Color(0xFF000000),
            primaryText = Color(0xFFFFFFFF),
            secondaryText = Color(0xFFFFEE58),
            accent = Color(0xFF00E5FF)
        )
    });

    fun colors(context: Context): WidgetColors = colorsProvider(context)

    companion object {
        fun fromKey(key: String?): VaccineWidgetTheme {
            return entries.find { it.key == key } ?: SYSTEM
        }
    }
}
