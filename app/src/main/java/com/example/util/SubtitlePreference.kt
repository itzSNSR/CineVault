package com.example.util

import com.example.data.model.SubtitleItem
import java.util.Locale

/**
 * Match subtitle languages by language, never by the URL or the previous
 * episode's SubtitleItem. Different episodes/providers can label English as
 * "English", "EN", "eng", or "English (CC)".
 */
object SubtitlePreference {
    /**
     * Convert a provider's human-readable language label into a BCP-47 primary
     * language tag accepted by Media3 (e.g. English -> en).
     */
    fun languageTag(label: String?): String? {
        val name = label?.substringBefore('(')?.substringBefore('[')
            ?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-') ?: return null
        if (name.isEmpty()) return null
        if (name == "english" || name.startsWith("english ") ||
            name == "en" || name == "eng" ||
            name.startsWith("en-") || name.startsWith("eng-")
        ) return "en"

        if (name.matches(Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*"))) {
            return name.substringBefore('-')
        }

        return Locale.getISOLanguages().firstOrNull { code ->
            Locale(code).getDisplayLanguage(Locale.ENGLISH).equals(name, ignoreCase = true)
        }
    }

    /**
     * Choose a SubtitleItem exclusively from the newly fetched episode.
     * Preserve the user's language if available; otherwise prefer English.
     * Explicit Off or embedded-caption choices take precedence.
     */
    fun forEpisode(
        available: List<SubtitleItem>,
        preferredLanguage: String?,
        subtitlesOff: Boolean,
        embeddedLanguage: String?
    ): SubtitleItem? {
        if (subtitlesOff || embeddedLanguage != null) return null
        val candidates = available.filter { it.url.isNotBlank() }
        if (candidates.isEmpty()) return null

        if (!preferredLanguage.isNullOrBlank()) {
            candidates.firstOrNull {
                it.language.equals(preferredLanguage, ignoreCase = true)
            }?.let { return it }

            val preferredTag = languageTag(preferredLanguage)
            if (preferredTag != null) {
                candidates.firstOrNull {
                    languageTag(it.language) == preferredTag
                }?.let { return it }
            }
        }

        return candidates.firstOrNull { languageTag(it.language) == "en" }
            ?: candidates.first()
    }
}
