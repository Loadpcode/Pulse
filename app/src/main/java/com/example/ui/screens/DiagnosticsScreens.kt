package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.example.ui.components.AppIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.ui.components.SyncedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.tvFocusHighlight
import com.example.ui.theme.MonoMetricStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    logs: List<String>,
    onClearLogs: () -> Unit,
    onCopied: () -> Unit,
    onShareLogs: () -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SyncedTopAppBar(
                title = { Text(stringResource(R.string.label_xray_logs)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onShareLogs,
                        enabled = logs.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                            .testTag("logs_share_button")
                    ) {
                        Icon(AppIcons.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                            onCopied()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                    ) {
                        Icon(AppIcons.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                    }
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                    ) {
                        Icon(AppIcons.DeleteSweep, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            )
        }
    ) { innerPadding ->
        LogsContent(
            logs = logs,
            onClearLogs = onClearLogs,
            onCopied = onCopied,
            onShareLogs = onShareLogs,
            showInlineActions = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun LogsContent(
    logs: List<String>,
    onClearLogs: () -> Unit,
    onCopied: () -> Unit,
    onShareLogs: () -> Unit,
    showInlineActions: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier) {
        if (showInlineActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_xray_logs),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onShareLogs,
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier
                        .tvFocusHighlight(cornerRadius = 16.dp)
                        .testTag("logs_inline_share_button")
                ) {
                    Icon(AppIcons.Share, contentDescription = null)
                    Text(
                        text = stringResource(R.string.action_share),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                        onCopied()
                    },
                    modifier = Modifier.tvFocusHighlight(cornerRadius = 16.dp)
                ) {
                    Icon(AppIcons.ContentCopy, contentDescription = null)
                    Text(
                        text = stringResource(R.string.action_copy),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                OutlinedButton(
                    onClick = onClearLogs,
                    modifier = Modifier.tvFocusHighlight(cornerRadius = 16.dp)
                ) {
                    Icon(AppIcons.DeleteSweep, contentDescription = null)
                    Text(
                        text = stringResource(R.string.action_clear),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = logs,
                    key = { index, _ -> index }
                ) { _, line ->
                    Text(
                        text = line,
                        style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigPreviewScreen(
    configJson: String,
    onRefresh: () -> Unit,
    onCopied: () -> Unit,
    onShareConfig: () -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SyncedTopAppBar(
                title = { Text(stringResource(R.string.label_config_preview)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onShareConfig,
                        enabled = configJson.isNotBlank(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                            .testTag("config_share_button")
                    ) {
                        Icon(AppIcons.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(configJson))
                            onCopied()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .tvFocusHighlight(cornerRadius = 14.dp)
                    ) {
                        Icon(AppIcons.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                    }
                }
            )
        }
    ) { innerPadding ->
        ConfigPreviewContent(
            configJson = configJson,
            onRefresh = onRefresh,
            onCopied = onCopied,
            onShareConfig = onShareConfig,
            showInlineActions = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun ConfigPreviewContent(
    configJson: String,
    onRefresh: () -> Unit,
    onCopied: () -> Unit,
    onShareConfig: () -> Unit,
    showInlineActions: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        onRefresh()
    }

    Column(modifier = modifier) {
        if (showInlineActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_config_preview),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onShareConfig,
                    enabled = configJson.isNotBlank(),
                    modifier = Modifier
                        .tvFocusHighlight(cornerRadius = 16.dp)
                        .testTag("config_inline_share_button")
                ) {
                    Icon(AppIcons.Share, contentDescription = null)
                    Text(
                        text = stringResource(R.string.action_share),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(configJson))
                        onCopied()
                    },
                    modifier = Modifier.tvFocusHighlight(cornerRadius = 16.dp)
                ) {
                    Icon(AppIcons.ContentCopy, contentDescription = null)
                    Text(
                        text = stringResource(R.string.action_copy),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            SelectionContainer {
                Text(
                    text = configJson.ifBlank { "{}" },
                    style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 860.dp)
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}
