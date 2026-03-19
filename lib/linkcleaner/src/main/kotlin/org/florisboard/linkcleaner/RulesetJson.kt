package org.florisboard.linkcleaner

import kotlinx.serialization.json.Json

internal object RulesetJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }
}
