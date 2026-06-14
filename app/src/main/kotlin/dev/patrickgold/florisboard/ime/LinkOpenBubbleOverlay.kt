/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package dev.patrickgold.florisboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import dev.patrickgold.florisboard.R
import kotlin.math.hypot

/**
 * Small draggable overlay bubble: tap opens [url] in the browser; auto-dismisses after a timeout.
 */
class LinkOpenBubbleOverlay(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var rootView: View? = null
    private var params: LayoutParams? = null
    private var dismissRunnable: Runnable? = null
    private var shownUrl: String? = null

    private val autoDismissMs = 45_000L
    private val dragThresholdPx = 16f

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show(url: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !Settings.canDrawOverlays(appContext)) {
            return
        }
        if (rootView != null && shownUrl == url) {
            scheduleDismiss()
            return
        }
        dismiss()
        shownUrl = url

        val view = LayoutInflater.from(appContext).inflate(R.layout.link_open_bubble, null)
        val btn = view.findViewById<android.widget.ImageButton>(R.id.link_open_bubble_btn)

        val lp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        // TOP|START so x/y are absolute screen coordinates and drag deltas behave naturally.
        lp.gravity = Gravity.TOP or Gravity.START
        val dm = appContext.resources.displayMetrics
        val marginPx = (8 * dm.density).toInt()
        // Estimate an ~48dp button so we land near the right edge before the view is measured.
        val approxSizePx = (48 * dm.density).toInt()
        lp.x = (dm.widthPixels - approxSizePx - marginPx).coerceAtLeast(0)
        lp.y = (dm.heightPixels / 2) - (approxSizePx / 2)

        var touchDownX = 0f
        var touchDownY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var dragging = false

        btn.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchDownX
                    val dy = event.rawY - touchDownY
                    if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > dragThresholdPx) {
                        dragging = true
                    }
                    if (dragging) {
                        lp.x += (event.rawX - lastRawX).toInt()
                        lp.y += (event.rawY - lastRawY).toInt()
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        try {
                            windowManager.updateViewLayout(view, lp)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        OpenLinkUtils.forwardToBrowser(appContext, url)
                        dismiss()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        try {
            windowManager.addView(view, lp)
        } catch (_: Exception) {
            shownUrl = null
            return
        }
        rootView = view
        params = lp
        scheduleDismiss()
    }

    fun dismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        val v = rootView ?: return
        rootView = null
        params = null
        shownUrl = null
        try {
            windowManager.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun scheduleDismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = Runnable { dismiss() }
        handler.postDelayed(dismissRunnable!!, autoDismissMs)
    }
}
