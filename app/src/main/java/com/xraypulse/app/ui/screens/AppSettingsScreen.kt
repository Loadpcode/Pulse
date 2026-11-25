package com.xraypulse.app.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.xraypulse.app.ui.components.AppIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import com.xraypulse.app.ui.components.SyncedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xraypulse.app.R
import com.xraypulse.app.model.AppLanguage
import com.xraypulse.app.model.AppThemeMode
import com.xraypulse.app.ui.components.SoftSlidingSelector
import com.xraypulse.app.ui.components.tvFocusHighlight
import com.xraypulse.app.ui.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    uiState: SettingsUiState,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onMarqueeChange: (Boolean) -> Unit,
    onOpenVisualSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SyncedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_item_app_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                            .testTag("app_settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        AppSettingsContent(
            uiState = uiState,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            onLanguageChange = onLanguageChange,
            onMarqueeChange = onMarqueeChange,
            onOpenVisualSettings = onOpenVisualSettings,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun AppSettingsContent(
    uiState: SettingsUiState,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onMarqueeChange: (Boolean) -> Unit,
    onOpenVisualSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = uiState.settings

    Box(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Theme Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_theme),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val darkLabel = stringResource(R.string.theme_dark)
                    val systemLabel = stringResource(R.string.theme_system)
                    val lightLabel = stringResource(R.string.theme_light)
                    SoftSlidingSelector(
                        options = AppThemeMode.entries,
                        selectedOption = settings.themeMode,
                        optionLabel = { mode ->
                            when (mode) {
                                AppThemeMode.DARK -> darkLabel
                                AppThemeMode.SYSTEM -> systemLabel
                                AppThemeMode.LIGHT -> lightLabel
                            }
                        },
                        onSelect = onThemeModeChange,
                        optionTestTag = { "settings_theme_${it.name}" }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.label_dynamic_color),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.label_dynamic_color_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.dynamicColorEnabled,
                                onCheckedChange = onDynamicColorChange,
                                modifier = Modifier
                                    .tvFocusHighlight(cornerRadius = 18.dp)
                                    .testTag("settings_dynamic_color_switch")
                            )
                        }
                    }
                }
            }

            // Visual Sub-screen Entry Card
            val visualCardShape = RoundedCornerShape(24.dp)
            Surface(
                shape = visualCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusHighlight(cornerRadius = 24.dp, scaleOnFocus = 1.015f)
                    .clip(visualCardShape)
                    .clickable(onClick = onOpenVisualSettings)
                    .testTag("app_settings_visual")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.Brush,
                                contentDescription = stringResource(R.string.settings_item_visual_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_item_visual_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_item_visual_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Language Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_language),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val langSystem = stringResource(R.string.lang_system)
                    val langRu = stringResource(R.string.lang_ru)
                    val langEn = stringResource(R.string.lang_en)
                    SoftSlidingSelector(
                        options = AppLanguage.entries,
                        selectedOption = settings.appLanguage,
                        optionLabel = { lang ->
                            when (lang) {
                                AppLanguage.SYSTEM -> langSystem
                                AppLanguage.RU -> langRu
                                AppLanguage.EN -> langEn
                            }
                        },
                        onSelect = onLanguageChange,
                        optionTestTag = { "settings_lang_${it.name}" }
                    )
                }
            }

            // Marquee / Long Title Scrolling Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_marquee_titles),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.label_marquee_titles_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.marqueeEnabled,
                        onCheckedChange = onMarqueeChange,
                        modifier = Modifier
                            .tvFocusHighlight(cornerRadius = 18.dp)
                            .testTag("settings_marquee_switch")
                    )
                }
            }
        }
    }
}
