/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package dev.patrickgold.florisboard.ime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Opens an http(s) URL with [Intent.ACTION_VIEW] like a normal link tap (browser / app chooser).
 */
object OpenLinkUtils {
    fun forwardToBrowser(context: Context, cleanedUrl: String) {
        val uri = Uri.parse(cleanedUrl)
        val baseIntent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val myPackage = appCtx.packageName

        val target = pickNonSelfHandler(pm, baseIntent, myPackage)
        if (target != null) {
            appCtx.startActivity(Intent(baseIntent).apply { component = target })
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
            if (pkgIntent.resolveActivity(pm) != null) {
                appCtx.startActivity(pkgIntent)
                return
            }
        }

        val chooser = Intent.createChooser(baseIntent, null).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(myPackage, OpenLinkActivity::class.java.name)),
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appCtx.startActivity(chooser)
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
                    .thenByDescending { it.second.priority },
            )
            .map { it.first }
            .firstOrNull()
    }
}
