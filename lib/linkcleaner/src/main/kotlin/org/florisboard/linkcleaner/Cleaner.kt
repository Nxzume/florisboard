package org.florisboard.linkcleaner

import io.ktor.http.ParametersBuilder
import io.ktor.http.Url
import io.ktor.http.formUrlEncode
import io.ktor.http.parameters
import kotlinx.serialization.decodeFromString

data class CleanResult(
    val original: String,
    val cleaned: String,
    val changed: Boolean,
    val notes: List<String> = emptyList(),
)

class Cleaner(
    private val ruleset: Ruleset,
) {
    companion object {
        fun fromRulesetJson(jsonString: String): Cleaner {
            val ruleset = RulesetJson.json.decodeFromString<Ruleset>(jsonString)
            return Cleaner(ruleset)
        }
    }

    fun cleanUrl(raw: String, maxPasses: Int = 3): CleanResult {
        val trimmed = raw.trim()
        val notes = mutableListOf<String>()

        val initialUrl = parseUrlOrNull(trimmed)
            ?: return CleanResult(original = raw, cleaned = raw, changed = false, notes = listOf("not_a_url"))

        var current = initialUrl
        var passes = 0
        var changedAny = false

        while (passes < maxPasses) {
            passes++
            val before = current.toString()

            val (unwrapped, unwrapNotes) = maybeUnwrapRedirector(current)
            if (unwrapped != null) {
                current = unwrapped
                notes += unwrapNotes
            }

            val cleaned = removeTrackingParams(current)
            current = cleaned

            val after = current.toString()
            if (after != before) changedAny = true
            if (after == before) break
        }

        val cleanedStr = current.toString()
        return CleanResult(
            original = raw,
            cleaned = cleanedStr,
            changed = changedAny || cleanedStr != raw,
            notes = notes.distinct(),
        )
    }

    fun cleanTextForUrls(text: String): String {
        val regex = UrlDetector.urlRegex
        return regex.replace(text) { match ->
            val url = match.value
            val result = cleanUrl(url)
            result.cleaned
        }
    }

    private fun maybeUnwrapRedirector(url: Url): Pair<Url?, List<String>> {
        val host = url.host.lowercase()
        val matches = ruleset.redirectors.firstOrNull { rule ->
            rule.hosts.any { it.equals(host, ignoreCase = true) }
        } ?: return null to emptyList()

        val queryParams = url.parameters
        for (key in matches.extractQueryParams) {
            val candidate = queryParams[key] ?: continue
            val candidateUrl = parseUrlOrNull(candidate) ?: continue
            val candidateHost = candidateUrl.host.lowercase()
            if (matches.allowHosts.isNotEmpty() && matches.allowHosts.none { it.equals(candidateHost, ignoreCase = true) }) {
                continue
            }
            return candidateUrl to listOf("unwrapped_redirector:$host")
        }
        return null to emptyList()
    }

    private fun removeTrackingParams(url: Url): Url {
        val host = url.host.lowercase()
        val baseParamRules = ruleset.paramRules

        val domainRules = baseParamRules.domainScoped.firstOrNull { scoped ->
            scoped.domains.any { it.equals(host, ignoreCase = true) || host.endsWith(".${it.lowercase()}") }
        }

        fun isAllowed(key: String): Boolean {
            val lower = key.lowercase()
            if (baseParamRules.allowExact.any { it.equals(lower, ignoreCase = true) }) return true
            if (domainRules?.allowExact?.any { it.equals(lower, ignoreCase = true) } == true) return true
            return false
        }

        fun shouldRemove(key: String): Boolean {
            val lower = key.lowercase()
            if (isAllowed(lower)) return false

            // Aggressive mode: treat all query params as tracking unless explicitly allowed.
            // This is intentionally heuristic; it may break some links that rely on query params
            // for correct navigation, so users can revert by setting aggressive=false.
            if (baseParamRules.aggressive) return true

            val removeExact = baseParamRules.removeExact.any { it.equals(lower, ignoreCase = true) }
            val removePrefix = baseParamRules.removePrefixes.any { lower.startsWith(it.lowercase()) }
            val domainRemoveExact = domainRules?.removeExact?.any { it.equals(lower, ignoreCase = true) } == true
            val domainRemovePrefix = domainRules?.removePrefixes?.any { lower.startsWith(it.lowercase()) } == true

            return removeExact || removePrefix || domainRemoveExact || domainRemovePrefix
        }

        val originalParams = url.parameters
        var anyRemoved = false
        val kept = ParametersBuilder()
        for (key in originalParams.names()) {
            if (shouldRemove(key)) {
                anyRemoved = true
            } else {
                val values = originalParams.getAll(key).orEmpty()
                values.forEach { kept.append(key, it) }
            }
        }
        if (!anyRemoved) return url

        val newParams = kept.build()
        val base = url.toString().substringBefore('?', missingDelimiterValue = url.toString())
        val q = newParams.formUrlEncode()
        val href = if (q.isEmpty()) base else "$base?$q"
        return Url(href)
    }

    private fun parseUrlOrNull(s: String): Url? {
        var candidate = s.trim()
        if (candidate.isEmpty()) return null

        // Many apps include trailing punctuation in copied links.
        candidate = candidate.trimEnd('.', ',', ';', ')', ']', '}')

        val normalized = if (
            candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true)
        ) {
            candidate
        } else {
            // Url() requires a scheme; when it is missing assume https.
            "https://$candidate"
        }

        return runCatching { Url(normalized) }.getOrNull()
    }
}
