package dev.teamworkmate.api.domain.saju

/** Classical five-element and heavenly-stem relations. Pure lookup tables. */
object SajuRelations {

    val STEM_ELEMENT: Map<String, Element> = mapOf(
        "甲" to Element.WOOD, "乙" to Element.WOOD,
        "丙" to Element.FIRE, "丁" to Element.FIRE,
        "戊" to Element.EARTH, "己" to Element.EARTH,
        "庚" to Element.METAL, "辛" to Element.METAL,
        "壬" to Element.WATER, "癸" to Element.WATER,
    )

    // 상생: 목→화→토→금→수→목
    private val GENERATES: Map<Element, Element> = mapOf(
        Element.WOOD to Element.FIRE,
        Element.FIRE to Element.EARTH,
        Element.EARTH to Element.METAL,
        Element.METAL to Element.WATER,
        Element.WATER to Element.WOOD,
    )

    // 상극: 목→토→수→화→금→목
    private val OVERCOMES: Map<Element, Element> = mapOf(
        Element.WOOD to Element.EARTH,
        Element.EARTH to Element.WATER,
        Element.WATER to Element.FIRE,
        Element.FIRE to Element.METAL,
        Element.METAL to Element.WOOD,
    )

    fun generates(a: Element, b: Element): Boolean = GENERATES[a] == b

    fun overcomes(a: Element, b: Element): Boolean = OVERCOMES[a] == b

    // 천간합: 甲己 乙庚 丙辛 丁壬 戊癸
    private val STEM_HAP: Set<Set<String>> = setOf(
        setOf("甲", "己"), setOf("乙", "庚"), setOf("丙", "辛"), setOf("丁", "壬"), setOf("戊", "癸"),
    )

    // 천간충: 甲庚 乙辛 丙壬 丁癸
    private val STEM_CHUNG: Set<Set<String>> = setOf(
        setOf("甲", "庚"), setOf("乙", "辛"), setOf("丙", "壬"), setOf("丁", "癸"),
    )

    fun isStemHap(a: String, b: String): Boolean = setOf(a, b) in STEM_HAP

    fun isStemChung(a: String, b: String): Boolean = setOf(a, b) in STEM_CHUNG
}
