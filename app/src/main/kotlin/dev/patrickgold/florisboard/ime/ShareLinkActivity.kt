/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package dev.patrickgold.florisboard.ime

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import org.florisboard.linkcleaner.LinkCleanerBridge

/**
 * Handles Share (ACTION_SEND text/plain): cleans URLs in the shared text.
 * If the result is a single http(s) URL, opens it in the browser; otherwise copies to clipboard.
 *
 * Workaround when links open in-app: from the in-app browser or link context menu, use
 * "Share" → FlorisBoard to get the cleaned link opened (or copied).
 */
class ShareLinkActivity : ComponentActivity() {
    private val prefs by FlorisPreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (raw.isNullOrBlank()) {
            finish()
            return
        }

        val cleaningEnabled = prefs.clipboard.linkCleanerEnabled.get()
        val processed = if (cleaningEnabled) {
            LinkCleanerBridge.sanitize(
                context = this,
                text = raw,
                aggressive = prefs.clipboard.linkCleanerAggressive.get(),
            ).trim()
        } else {
            raw.trim()
        }

        val singleUrl = extractSingleUrl(processed)
        if (singleUrl != null) {
            OpenLinkUtils.forwardToBrowser(this, singleUrl)
        } else if (processed.isNotEmpty()) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(null, processed))
            val toast = if (cleaningEnabled) {
                "Cleaned, copied to clipboard"
            } else {
                "Copied to clipboard"
            }
            Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    /**
     * If [s] is a single http(s) URL (possibly surrounded by whitespace) returns the trimmed URL,
     * otherwise null.
     */
    private fun extractSingleUrl(s: String): String? {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return null
        // Must be a single line with no internal whitespace.
        if (trimmed.any { it.isWhitespace() }) return null
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> null
        }
    }
}
