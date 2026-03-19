/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package dev.patrickgold.florisboard.ime

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
        forwardToBrowser(processed)
        finish()
    }

    private fun forwardToBrowser(cleanedUrl: String) {
        val uri = Uri.parse(cleanedUrl)
        val baseIntent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val myPackage = packageName

        // Prefer system default / first resolver
        val target = pickNonSelfHandler(packageManager, baseIntent, myPackage)
        if (target != null) {
            startActivity(Intent(baseIntent).apply { component = target })
            return
        }

        // Hardened ROMs may hide handlers; try known browsers
        val fallbackPackages = listOf(
            "app.vanadium.browser",
            "com.android.chrome",
            "org.mozilla.firefox",
            "org.mozilla.fenix",
            "com.brave.browser",
        )
        for (pkg in fallbackPackages) {
            if (pkg == myPackage) continue
            val pkgIntent = Intent(baseIntent).setPackage(pkg)
            if (pkgIntent.resolveActivity(packageManager) != null) {
                startActivity(pkgIntent)
                return
            }
        }

        // Last resort: chooser (exclude this activity to avoid loop)
        val chooser = Intent.createChooser(baseIntent, null).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(myPackage, OpenLinkActivity::class.java.name)))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(chooser)
    }

    private fun pickNonSelfHandler(
        pm: PackageManager,
        intent: Intent,
        myPackage: String,
    ): ComponentName? {
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == myPackage) return@mapNotNull null
                ComponentName(ai.packageName, ai.name) to ri
            }
            .sortedWith(
                compareByDescending<Pair<ComponentName, android.content.pm.ResolveInfo>> { it.second.match }
                    .thenByDescending { it.second.priority }
            )
            .map { it.first }
            .firstOrNull()
    }
}
