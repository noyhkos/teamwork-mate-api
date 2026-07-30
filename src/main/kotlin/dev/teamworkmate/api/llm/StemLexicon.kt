package dev.teamworkmate.api.llm

/**
 * Deterministic persona dictionary for the 10 day-master stems.
 * The LLM may only use metaphors from here — rich copy without hallucination.
 */
object StemLexicon {

    data class Persona(val ko: String, val symbol: String, val keywords: List<String>)

    private val PERSONAS = mapOf(
        "甲" to Persona("갑목", "곧게 자라는 큰 나무", listOf("올곧음", "개척", "성장")),
        "乙" to Persona("을목", "바람에 휘어도 꺾이지 않는 덩굴", listOf("유연함", "생존력", "섬세함")),
        "丙" to Persona("병화", "한낮의 태양", listOf("존재감", "열정", "확산")),
        "丁" to Persona("정화", "어둠을 밝히는 촛불", listOf("집중", "온기", "통찰")),
        "戊" to Persona("무토", "흔들리지 않는 큰 산", listOf("듬직함", "포용", "중심")),
        "己" to Persona("기토", "무엇이든 키워내는 기름진 밭", listOf("실속", "양육", "수용")),
        "庚" to Persona("경금", "단단한 무쇠와 바위", listOf("결단", "추진", "의리")),
        "辛" to Persona("신금", "벼려낸 보석과 칼날", listOf("예리함", "완벽주의", "세련됨")),
        "壬" to Persona("임수", "막힘없이 흐르는 큰 강", listOf("지혜", "스케일", "융통성")),
        "癸" to Persona("계수", "스며드는 이슬비", listOf("직관", "적응", "온화함")),
    )

    fun of(stem: String): Persona =
        PERSONAS[stem] ?: Persona(stem, "알 수 없는 기운", listOf("신비로움"))
}
