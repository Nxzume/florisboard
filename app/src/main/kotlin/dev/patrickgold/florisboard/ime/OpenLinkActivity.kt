/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package dev.patrickgold.florisboard.ime

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import org.florisboard.linkcleaner.LinkCleanerBridge

/**
 * Handles VIEW http/https intents: cleans the URL (strip tracking params, unwrap redirectors)
 * and opens the result in the user's browser. Register FlorisBoard as default/open-with for
 * links to get "clicked links cleaned" without a separate app.
 */
class OpenLinkActivity : ComponentActivity() {
    private val prefs by FlorisPreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawUrl = intent?.dataString
        if (rawUrl.isNullOrBlank()) {
            finish()
            return
        }

        val processed = if (prefs.clipboard.linkCleanerEnabled.get()) {
            LinkCleanerBridge.sanitize(
                context = this,
                text = rawUrl,
                aggressive = prefs.clipboard.linkCleanerAggressive.get(),
            )
        } else {
            rawUrl
        }
        OpenLinkUtils.forwardToBrowser(this, processed)
        finish()
    }
}
