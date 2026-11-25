package com.example.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.example.model.AppLanguage
import com.example.ui.theme.AmberLatency
import com.example.ui.theme.CoralError
import com.example.ui.theme.MintAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val EXPIRE_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
}

fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1024L -> "$safe B"
        safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024.0)
        safe < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", safe / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatDuration(elapsedMillis: Long): String {
    val totalSec = (elapsedMillis / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
}

fun formatExpireDate(expireTimestampSec: Long): String {
    if (expireTimestampSec <= 0L) return ""
    val formatter = EXPIRE_DATE_FORMAT.get() ?: SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return formatter.format(Date(expireTimestampSec * 1000L))
}

fun latencyBadgeColor(latencyMs: Long?): Color {
    return when {
        latencyMs == null -> Color(0xFF9FB0C7)
        latencyMs < 0L -> CoralError
        latencyMs < 130L -> MintAccent
        latencyMs < 320L -> AmberLatency
        else -> CoralError
    }
}

fun Context.createLocalizedContext(language: AppLanguage): Context {
    val tag = language.tag ?: return this
    val locale = Locale.forLanguageTag(tag)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    val localized = createConfigurationContext(config)
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localized.resources
        override fun getAssets(): AssetManager = localized.assets
        override fun getTheme(): Resources.Theme = this@createLocalizedContext.theme
    }
}

@Composable
fun rememberLocalizedContext(baseContext: Context, language: AppLanguage): Context {
    return remember(baseContext, language) {
        baseContext.createLocalizedContext(language)
    }
}
