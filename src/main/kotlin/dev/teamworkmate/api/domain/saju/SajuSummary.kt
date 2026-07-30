package dev.teamworkmate.api.domain.saju

/** Five elements (오행). */
enum class Element(val ko: String) {
    WOOD("목"), FIRE("화"), EARTH("토"), METAL("금"), WATER("수");

    companion object {
        fun fromKo(ko: String): Element = entries.first { it.ko == ko }
    }
}

/** Ten-god groups (십신 5분류). */
enum class TenGodGroup(val ko: String, val members: Set<String>) {
    BIGYEOP("비겁", setOf("비견", "겁재")),
    SIKSANG("식상", setOf("식신", "상관")),
    JAESEONG("재성", setOf("편재", "정재")),
    GWANSEONG("관성", setOf("편관", "정관")),
    INSEONG("인성", setOf("편인", "정인"));

    companion object {
        fun ofGod(god: String): TenGodGroup? = entries.firstOrNull { god in it.members }
    }
}

enum class DayStrength { STRONG, NEUTRAL, WEAK }

/**
 * Engine input distilled from the calc service response.
 * When birth time is unknown, counts cover 3 pillars only and [strength] is null.
 */
data class SajuSummary(
    val elementCounts: Map<Element, Int>,
    val tenGodCounts: Map<TenGodGroup, Int>,
    val strength: DayStrength?,
    val birthTimeKnown: Boolean,
) {
    val elementTotal: Int = elementCounts.values.sum()
    val tenGodTotal: Int = tenGodCounts.values.sum()

    fun elementRatio(e: Element): Double =
        if (elementTotal == 0) 0.0 else elementCounts.getOrDefault(e, 0).toDouble() / elementTotal

    fun tenGodRatio(g: TenGodGroup): Double =
        if (tenGodTotal == 0) 0.0 else tenGodCounts.getOrDefault(g, 0).toDouble() / tenGodTotal

    companion object {
        /** Builds from raw korean god names, ignoring the day-master placeholder. */
        fun fromRaw(
            elementsKo: Map<String, Int>,
            gods: List<String>,
            strength: DayStrength?,
            birthTimeKnown: Boolean,
        ): SajuSummary {
            val elems = elementsKo.entries.associate { (k, v) -> Element.fromKo(k) to v }
            val godCounts = gods.mapNotNull { TenGodGroup.ofGod(it) }
                .groupingBy { it }.eachCount()
            return SajuSummary(elems, godCounts, strength, birthTimeKnown)
        }
    }
}
