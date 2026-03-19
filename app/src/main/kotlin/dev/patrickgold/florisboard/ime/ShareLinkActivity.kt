/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package dev.patrickgold.florisboard.ime

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

        val processed = if (prefs.clipboard.linkCleanerEnabled.get()) {
            LinkCleanerBridge.sanitize(
                context = this,
                text = raw,
                aggressive = prefs.clipboard.linkCleanerAggressive.get(),
            ).trim()
        } else {
            raw.trim()
        }

        if (processed.isNotEmpty() && looksLikeSingleUrl(processed)) {
            forwardToBrowser(processed)
        } else {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText(null, processed))
            val toast = if (prefs.clipboard.linkCleanerEnabled.get()) {
                "Cleaned, copied to clipboard"
            } else {
                "Copied to clipboard"
            }
            Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun looksLikeSingleUrl(s: String): Boolean {
        val line = s.lines().firstOrNull()?.trim() ?: return false
        return line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true)
    }

    private fun forwardToBrowser(cleanedUrl: String) {
        val uri = Uri.parse(cleanedUrl)
        val baseIntent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val myPackage = packageName

        var target = pickNonSelfHandler(packageManager, baseIntent, myPackage)
        if (target != null) {
            startActivity(Intent(baseIntent).apply { component = target })
            return
        }

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

        val chooser = Intent.createChooser(baseIntent, null).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(
                ComponentName(myPackage, OpenLinkActivity::class.java.name),
                ComponentName(myPackage, ShareLinkActivity::class.java.name),
            ))
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
