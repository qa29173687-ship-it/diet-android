package tw.dietbook

/** 全形數字／小數點 → 半形，再解析；空白或無法解析回 null。 */
object Numbers {
    fun normalize(raw: String?): String {
        if (raw == null) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw.trim()) {
            when (ch) {
                in '０'..'９' -> sb.append(('0'.code + (ch.code - 0xFF10)).toChar()) // ０-９
                '．', '。', '・', '‧' -> sb.append('.')
                '，' -> sb.append('.') // 偶發全形逗號當小數
                '－', '—', '–' -> sb.append('-')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun doubleOrNull(raw: String?): Double? = normalize(raw).toDoubleOrNull()
    fun intOrNull(raw: String?): Int? = normalize(raw).toDoubleOrNull()?.toInt()
}
