package org.florisboard.linkcleaner

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * Sanitizes tracking params / unwraps redirectors in URLs embedded in [text].
 * Thread-safe lazy init from bundled [R.raw.linkcleaner_default_rules].
 */
object LinkCleanerBridge {
    private val rulesetRef = AtomicReference<Ruleset?>(null)
    private val conservativeCleanerRef = AtomicReference<Cleaner?>(null)
    private val aggressiveCleanerRef = AtomicReference<Cleaner?>(null)

    fun sanitize(context: Context, text: String): String =
        sanitize(context, text, aggressive = getBundledAggressive(context))

    fun sanitize(context: Context, text: String, aggressive: Boolean): String {
        if (text.isEmpty()) return text
        val cleaner = getCleaner(context, aggressive)
        return cleaner.cleanTextForUrls(text)
    }

    private fun getBundledAggressive(context: Context): Boolean {
        val ruleset = getRuleset(context)
        return ruleset.paramRules.aggressive
    }

    private fun getCleaner(context: Context, aggressive: Boolean): Cleaner {
        val ref = if (aggressive) aggressiveCleanerRef else conservativeCleanerRef
        ref.get()?.let { return it }

        val ruleset = getRuleset(context)
        val updated = ruleset.copy(
            paramRules = ruleset.paramRules.copy(
                aggressive = aggressive,
            ),
        )
        val cleaner = Cleaner(updated)
        ref.compareAndSet(null, cleaner)
        return ref.get()!!
    }

    private fun getRuleset(context: Context): Ruleset {
        rulesetRef.get()?.let { return it }

        val json = context.resources
            .openRawResource(R.raw.linkcleaner_default_rules)
            .bufferedReader()
            .use { it.readText() }

        val decoded = RulesetJson.json.decodeFromString<Ruleset>(json)
        rulesetRef.compareAndSet(null, decoded)
        return rulesetRef.get()!!
    }
}
