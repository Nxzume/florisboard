package org.florisboard.linkcleaner

internal object UrlDetector {
    val urlRegex: Regex =
        // Matches both scheme URLs (http(s)://...) and scheme-less ones (youtube.com/..., youtu.be/...)
        // including a path or query to avoid rewriting random domains.
        Regex(
            """(?:https?://)?(?:www\.)?[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?:/[^\s<>"'()]+|\?[^\s<>"'()]+|#[^\s<>"'()]+)""",
            RegexOption.IGNORE_CASE
        )
}
