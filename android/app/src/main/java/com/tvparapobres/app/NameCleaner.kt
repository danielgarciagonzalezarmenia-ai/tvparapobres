package com.tvparapobres.app

/**
 * Limpia nombres de canales/peliculas/series:
 * reemplaza glyphs ornamentales (Ə → E, $ → S, Ø → O...),
 * elimina emojis, years, calidades, y puntuación rara.
 */
object NameCleaner {
    private val LTR = mapOf(
        'Ə' to 'E', 'ǝ' to 'e', 'Ǝ' to 'E', 'È' to 'E', 'É' to 'E', 'Ê' to 'E',
        'Š' to 'S', '$' to 'S', 'Ø' to 'O', 'Ö' to 'O', '0' to 'O', '1' to 'I',
        '3' to 'E', '5' to 'S', '7' to 'T', '@' to 'A', 'Ä' to 'A'
    )

    fun clean(s: String): String {
        var r = s
        for ((ch, rep) in LTR) r = r.replace(ch, rep)
        r = r.replace(Regex("\\p{Extended_Pictographic}|\\p{Emoji_Presentation}|\\uFE0F|\\u200D"), " ")
        r = r.replace(Regex("[*.,]"), "")
        r = r.replace(Regex("\\b(?:19|20)\\d{2}(?:/\\d{1,4})?\\b"), " ")
        r = r.replace(Regex("\\b(?:UHD|4K|HDR10?|DOLBY\\s*ATMOS|DTS[\\s-]?HD|MP4?|MKV|XVID|H\\.?264|HEVC)\\b", RegexOption.IGNORE_CASE), " ")
        r = r.replace(Regex("[()\\[\\]]"), " ")
        r = r.replace(Regex("[|•··…*.\"'`´_~/]"), " ")
        r = r.replace(Regex("\\s*[:;]\\s*"), " ")
        r = r.replace(Regex("\\s{2,}"), " ")
        r = r.replace(Regex("^[\\s.,|:;—–-]+|[\\s.,|:;—–-]+$"), "")
        return r.trim()
    }
}