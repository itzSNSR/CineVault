package com.example.util

import com.example.data.model.SubtitleItem
import java.util.Locale

/**
 * Subtitle language matching and Media3 tag normalization.
 *
 * Providers label the same language in many ways ("English", "EN", "eng",
 * "English (CC)", "English (OpenSubtitles)", "Tamil", "தமிழ்"). Media3 only
 * matches tracks reliably with BCP-47 primary tags such as "en" or "ta", so
 * every provider label is normalized before it reaches the player.
 *
 * A [SubtitleItem] always belongs to the episode it was fetched with. Selection
 * therefore has to be re-run for each episode: only the language preference is
 * persisted, never a URL or the previous episode's item.
 */
object SubtitlePreference {
    const val ENGLISH = "en"
    const val UNDETERMINED = "und"

    /**
     * Display labels and native names used by the providers, mapped to a
     * Media3-friendly primary language tag. Two/three letter codes are also
     * handled generically by [languageTag].
     */
    private val explicitTags: Map<String, String> = mapOf(
        "en" to ENGLISH, "eng" to ENGLISH, "english" to ENGLISH,
        "und" to UNDETERMINED, "undetermined" to UNDETERMINED, "unknown" to UNDETERMINED,
        "bn" to "bn", "ben" to "bn", "bangla" to "bn", "bengali" to "bn", "বাংলা" to "bn",
        "hi" to "hi", "hin" to "hi", "hindi" to "hi", "हिन्दी" to "hi",
        "ta" to "ta", "tam" to "ta", "tamil" to "ta", "தமிழ்" to "ta",
        "te" to "te", "tel" to "te", "telugu" to "te", "తెలుగు" to "te",
        "kn" to "kn", "kan" to "kn", "kannada" to "kn", "ಕನ್ನಡ" to "kn",
        "ml" to "ml", "mal" to "ml", "malayalam" to "ml", "മലയാളം" to "ml",
        "gu" to "gu", "guj" to "gu", "gujarati" to "gu", "ગુજરાતી" to "gu",
        "pa" to "pa", "pan" to "pa", "punjabi" to "pa", "ਪੰਜਾਬੀ" to "pa",
        "ar" to "ar", "ara" to "ar", "arabic" to "ar", "العَرَبِيةُ" to "ar", "العربية" to "ar",
        "ur" to "ur", "urd" to "ur", "urdu" to "ur", "اُردُو" to "ur", "اردو" to "ur",
        "fil" to "fil", "tl" to "fil", "tgl" to "fil", "tagalog" to "fil", "filipino" to "fil",
        "fr" to "fr", "fra" to "fr", "fre" to "fr", "french" to "fr", "français" to "fr",
        "id" to "id", "ind" to "id", "indonesian" to "id",
        "ms" to "ms", "may" to "ms", "msa" to "ms", "malay" to "ms",
        "pt" to "pt", "por" to "pt", "portuguese" to "pt", "português" to "pt",
        "ru" to "ru", "rus" to "ru", "russian" to "ru", "русский" to "ru",
        "th" to "th", "tha" to "th", "thai" to "th", "ภาษาไทย" to "th",
        "vi" to "vi", "vie" to "vi", "vietnamese" to "vi", "tiếng việt" to "vi",
        "zh" to "zh", "zho" to "zh", "chi" to "zh", "chinese" to "zh", "中文" to "zh",
        "ja" to "ja", "jpn" to "ja", "japanese" to "ja", "日本語" to "ja",
        "ko" to "ko", "kor" to "ko", "korean" to "ko", "한국어" to "ko",
        "es" to "es", "spa" to "es", "spanish" to "es", "español" to "es",
        "de" to "de", "deu" to "de", "ger" to "de", "german" to "de", "deutsch" to "de",
        "it" to "it", "ita" to "it", "italian" to "it", "italiano" to "it",
        "tr" to "tr", "tur" to "tr", "turkish" to "tr", "türkçe" to "tr",
        "el" to "el", "ell" to "el", "greek" to "el", "ελληνικά" to "el",
        "he" to "he", "heb" to "he", "hebrew" to "he", "עברית" to "he",
        "pl" to "pl", "pol" to "pl", "polish" to "pl", "polski" to "pl",
        "nl" to "nl", "nld" to "nl", "dut" to "nl", "dutch" to "nl", "nederlands" to "nl",
        "in" to "id"
    )

    /** English display names for every ISO code, used as a last-resort lookup. */
    private val displayNameTags: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (code in Locale.getISOLanguages()) {
            val name = Locale(code).getDisplayLanguage(Locale.ENGLISH)
            if (name.isBlank() || name.equals(code, ignoreCase = true)) continue
            val key = name.lowercase(Locale.ROOT)
            if (!map.containsKey(key)) map[key] = code
        }
        map
    }

    /**
     * Normalizes a provider label ("English (CC)", "ENG", "en-US", "தமிழ்") to
     * a primary BCP-47 tag ("en", "en", "en", "ta"). Returns null when the
     * label carries no recognizable language.
     */
    fun languageTag(label: String?): String? {
        val raw = label?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val base = raw
            .substringBefore('(')
            .substringBefore('[')
            .substringBefore('{')
            .substringBefore('/')
            .trim()
            .ifEmpty { raw }
        val cleaned = base
            .lowercase(Locale.ROOT)
            .replace('_', '-')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return null

        explicitTags[cleaned]?.let { return it }
        val firstWord = cleaned.substringBefore(' ')
        explicitTags[firstWord]?.let { return it }

        // Plain ISO codes such as "en", "eng" or regional variants like "en-US".
        if (cleaned.matches(Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*"))) {
            return cleaned.substringBefore('-')
        }

        displayNameTags[cleaned]?.let { return it }
        displayNameTags[firstWord]?.let { return it }
        return null
    }

    fun isEnglish(label: String?): Boolean = languageTag(label) == ENGLISH

    /**
     * Picks a subtitle from the newly fetched episode only.
     *
     * Priority: explicit Off, the user's saved language, English, then any valid
     * caption of this episode. A previous episode's item/URL is never considered.
     *
     * @param allowAutomaticEnglish false for dubbed audio, where CineVault keeps
     * automatic caption selection off (a manual language still applies).
     */
    fun forEpisode(
        available: List<SubtitleItem>,
        preferredLanguage: String?,
        subtitlesOff: Boolean,
        allowAutomaticEnglish: Boolean = true,
        embeddedLanguage: String? = null
    ): SubtitleItem? {
        if (subtitlesOff || embeddedLanguage != null) return null
        val candidates = available.filter { it.url.isNotBlank() }
        if (candidates.isEmpty()) return null

        val preferredTag = languageTag(preferredLanguage)
        if (preferredTag != null) {
            candidates.firstOrNull { languageTag(it.language) == preferredTag }?.let { return it }
        }
        if (!allowAutomaticEnglish) return null

        return candidates.firstOrNull { languageTag(it.language) == ENGLISH } ?: candidates.firstOrNull()
    }
}
