/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package dev.patrickgold.florisboard.ime

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shows [LinkOpenBubbleOverlay] when the clipboard has an openable URL, the setting is on,
 * the keyboard is hidden, and overlay permission is granted.
 */
class LinkOpenBubbleCoordinator(
    private val app: FlorisApplication,
) {
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val overlay = LinkOpenBubbleOverlay(app)
    private val clipboardManager get() = app.clipboardManager.value

    fun start() {
        scope.launch {
            app.preferenceStoreLoaded.first { it }
            combine(
                prefs.clipboard.linkOpenBubbleEnabled.asFlow(),
                clipboardManager.openableClipboardUrlFlow,
                ImeInputViewVisibility.isShown,
            ) { enabled, url, inputShown ->
                Triple(enabled, url, inputShown)
            }.collect { (enabled, url, inputShown) ->
                val canOverlay = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(app)
                val km = app.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val locked = km?.isKeyguardLocked == true || km?.isDeviceLocked == true
                if (enabled && url != null && !inputShown && canOverlay && !locked) {
                    overlay.show(url)
                } else {
                    overlay.dismiss()
                }
            }
        }
    }

    fun shutdown() {
        scope.cancel()
        overlay.dismiss()
    }
}
