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

    private val R_EMOJI = Regex("\\p{Extended_Pictographic}|\\p{Emoji_Presentation}|\\uFE0F|\\u200D")
    private val R_PUNCT = Regex("[*.,]")
    private val R_YEAR = Regex("\\b(?:19|20)\\d{2}(?:/\\d{1,4})?\\b")
    private val R_QUAL = Regex("\\b(?:UHD|4K|HDR10?|DOLBY\\s*ATMOS|DTS[\\s-]?HD|MP4?|MKV|XVID|H\\.?264|HEVC)\\b", RegexOption.IGNORE_CASE)
    private val R_BRACK = Regex("[()\\[\\]]")
    private val R_SYM = Regex("[|•··…*.\"'`´_~/]")
    private val R_COLON = Regex("\\s*[:;]\\s*")
    private val R_SPACES = Regex("\\s{2,}")
    private val R_TRIM = Regex("^[\\s.,|:;—–-]+|[\\s.,|:;—–-]+$")

    fun clean(s: String): String {
        var r = s
        for ((ch, rep) in LTR) r = r.replace(ch, rep)
        r = r.replace(R_EMOJI, " ")
        r = r.replace(R_PUNCT, "")
        r = r.replace(R_YEAR, " ")
        r = r.replace(R_QUAL, " ")
        r = r.replace(R_BRACK, " ")
        r = r.replace(R_SYM, " ")
        r = r.replace(R_COLON, " ")
        r = r.replace(R_SPACES, " ")
        r = r.replace(R_TRIM, "")
        return r.trim()
    }
}