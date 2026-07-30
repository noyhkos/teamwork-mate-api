package dev.teamworkmate.api.domain.mbti

data class Mbti(
    val extraverted: Boolean, // E vs I
    val sensing: Boolean, // S vs N
    val thinking: Boolean, // T vs F
    val judging: Boolean, // J vs P
) {
    val code: String =
        "${if (extraverted) 'E' else 'I'}${if (sensing) 'S' else 'N'}${if (thinking) 'T' else 'F'}${if (judging) 'J' else 'P'}"

    companion object {
        private val PATTERN = Regex("^[EI][SN][TF][JP]$")

        fun parse(code: String): Mbti {
            val c = code.trim().uppercase()
            require(PATTERN.matches(c)) { "invalid MBTI: $code" }
            return Mbti(c[0] == 'E', c[1] == 'S', c[2] == 'T', c[3] == 'J')
        }
    }
}
