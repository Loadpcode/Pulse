package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent

object ShareUtils {
    private const val MAX_SHARE_TEXT_CHARS = 90_000
    private val supportedViewSchemes = setOf("vless", "vmess", "trojan", "hysteria2", "hy2")

    fun capShareText(
        text: String,
        maxChars: Int = MAX_SHARE_TEXT_CHARS,
        preferTail: Boolean = false
    ): String {
        val clean = text.trim()
        if (clean.length <= maxChars || maxChars <= 0) return clean
        val marker = "...\n"
        val budget = (maxChars - marker.length).coerceAtLeast(1)
        return if (preferTail) {
            val rawStart = (clean.length - budget).coerceAtLeast(0)
            val nextLine = clean.indexOf('\n', rawStart)
            val cutIndex = if (nextLine in rawStart until clean.length - 1 && (nextLine - rawStart) < 512) {
                nextLine + 1
            } else {
                rawStart
            }
            marker + clean.substring(cutIndex)
        } else {
            val rawEnd = budget.coerceAtMost(clean.length)
            val prevLine = clean.lastIndexOf('\n', rawEnd)
            val cutIndex = if (prevLine > 0 && (rawEnd - prevLine) < 512) {
                prevLine
            } else {
                rawEnd
            }
            clean.substring(0, cutIndex) + "\n..."
        }
    }

    fun createShareTextIntent(
        text: String,
        chooserTitle: String,
        subject: String? = null,
        preferTail: Boolean = false
    ): Intent {
        val safeText = capShareText(text, preferTail = preferTail)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, safeText)
            if (!subject.isNullOrBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }

    fun launchShareText(
        context: Context,
        text: String,
        chooserTitle: String,
        subject: String? = null,
        preferTail: Boolean = false,
        onWillLaunchExternalPrompt: () -> Unit = {}
    ): Boolean {
        val cleanText = capShareText(text, preferTail = preferTail)
        if (cleanText.isEmpty()) return false
        onWillLaunchExternalPrompt()
        val chooser = createShareTextIntent(
            text = cleanText,
            chooserTitle = chooserTitle,
            subject = subject,
            preferTail = preferTail
        )
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
        }.isSuccess
    }

    fun extractIncomingShareText(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val extra = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                extra?.trim()?.takeIf { it.isNotEmpty() }
            }
            Intent.ACTION_VIEW -> {
                val scheme = intent.data?.scheme?.lowercase()
                if (scheme != null && scheme in supportedViewSchemes) {
                    (intent.dataString ?: intent.data?.toString())?.trim()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
            else -> null
        }
    }

    fun clearIncomingSharePayload(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.removeExtra(Intent.EXTRA_TEXT)
                intent.removeExtra(Intent.EXTRA_SUBJECT)
                intent.clipData = null
                intent.action = Intent.ACTION_MAIN
            }
            Intent.ACTION_VIEW -> {
                intent.data = null
                intent.action = Intent.ACTION_MAIN
            }
        }
    }
}
