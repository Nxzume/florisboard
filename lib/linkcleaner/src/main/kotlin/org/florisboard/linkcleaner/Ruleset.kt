/*
 * Link cleaning rules (JSON-serializable). Derived from LinkCleaner core.
 */
package org.florisboard.linkcleaner

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Ruleset(
    val version: Int = 1,
    val paramRules: ParamRules = ParamRules(),
    val redirectors: List<RedirectorRule> = emptyList(),
)

@Serializable
data class ParamRules(
    val removeExact: Set<String> = emptySet(),
    val removePrefixes: Set<String> = emptySet(),
    val allowExact: Set<String> = emptySet(),
    val aggressive: Boolean = false,
    val domainScoped: List<DomainScopedParamRules> = emptyList(),
)

@Serializable
data class DomainScopedParamRules(
    val domains: Set<String> = emptySet(),
    val removeExact: Set<String> = emptySet(),
    val removePrefixes: Set<String> = emptySet(),
    val allowExact: Set<String> = emptySet(),
)

@Serializable
data class RedirectorRule(
    val hosts: Set<String> = emptySet(),
    val extractQueryParams: List<String> = listOf("url", "u", "q", "target", "dest", "destination"),
    @SerialName("allowHosts")
    val allowHosts: Set<String> = emptySet(),
)
