package org.florisboard.linkcleaner

import android.content.Context
import android.net.Uri
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

    /**
     * Returns the first http(s) URL in [text] suitable for [android.content.Intent.ACTION_VIEW],
     * or null. When [clean] is true, applies [sanitize] to each candidate segment first.
     */
    fun firstOpenableHttpUrl(
        context: Context,
        text: String,
        clean: Boolean,
        aggressive: Boolean,
    ): String? {
        if (text.isEmpty()) return null
        var searchFrom = 0
        while (searchFrom < text.length) {
            val match = UrlDetector.urlRegex.find(text, searchFrom) ?: return null
            val segment = match.value.trimEnd('.', ',', ';', ')', ']', '}')
            val processed = if (clean) {
                sanitize(context, segment, aggressive).trim()
            } else {
                segment
            }
            val withScheme = when {
                processed.startsWith("http://", ignoreCase = true) ||
                    processed.startsWith("https://", ignoreCase = true) -> processed
                else -> "https://$processed"
            }
            val uri = Uri.parse(withScheme)
            val scheme = uri.scheme?.lowercase()
            if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
                return withScheme
            }
            searchFrom = match.range.last + 1
        }
        return null
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
