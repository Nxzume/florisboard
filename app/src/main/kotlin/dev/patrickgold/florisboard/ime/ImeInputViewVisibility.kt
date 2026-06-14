/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package dev.patrickgold.florisboard.ime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the IME input view is currently shown (keyboard visible). Used to avoid duplicate
 * "open link" UI (smartbar vs floating bubble).
 */
object ImeInputViewVisibility {
    private val _isShown = MutableStateFlow(false)
    val isShown: StateFlow<Boolean> = _isShown

    fun setInputViewShown(shown: Boolean) {
        _isShown.value = shown
    }
}
