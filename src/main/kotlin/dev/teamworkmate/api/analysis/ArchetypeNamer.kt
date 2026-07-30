package dev.teamworkmate.api.analysis

import dev.teamworkmate.api.domain.traits.Trait

/** Deterministic team archetype naming from trait averages. Fun copy, zero LLM. */
object ArchetypeNamer {

    private val HEAD = mapOf(
        Trait.DRIVE to "추진력 폭발",
        Trait.CAUTION to "돌다리 정밀타격",
        Trait.SOCIAL to "인싸 에너지",
        Trait.DETAIL to "디테일 장인",
        Trait.CREATIVE to "아이디어 폭풍",
        Trait.HARMONY to "평화 유지",
        Trait.COMMAND to "사령탑",
        Trait.STEADY to "든든한 반석",
    )

    private val TAIL = mapOf(
        Trait.DRIVE to "돌격대",
        Trait.CAUTION to "안전위원회",
        Trait.SOCIAL to "축제기획단",
        Trait.DETAIL to "연구소",
        Trait.CREATIVE to "공작소",
        Trait.HARMONY to "힐링캠프",
        Trait.COMMAND to "지휘본부",
        Trait.STEADY to "요새",
    )

    private val RISK = mapOf(
        Trait.DRIVE to "시동 거는 사람이 없어요 — 일단 출발 버튼을 눌러줄 사람을 정해두면 좋아요",
        Trait.CAUTION to "다들 액셀만 밟는 팀 — 브레이크 담당이 없으니 결정 전에 한 박자 쉬어가요",
        Trait.SOCIAL to "다들 조용한 편 — 아이스브레이킹은 의식적으로 챙겨야 해요",
        Trait.DETAIL to "마무리 디테일이 약한 팀 — 체크리스트가 특효약이에요",
        Trait.CREATIVE to "새로운 시도가 잘 안 나오는 팀 — 브레인스토밍 시간을 따로 잡아보세요",
        Trait.HARMONY to "조율 담당이 없어요 — 의견 충돌 시 중재 규칙을 미리 정해두면 좋아요",
        Trait.COMMAND to "결정을 내려줄 사람이 없어요 — 사안별 결정권자를 정해두세요",
        Trait.STEADY to "텐션이 널뛰는 팀 — 루틴과 정기 일정이 안정감을 줘요",
    )

    data class Archetype(val name: String, val desc: String, val riskNote: String)

    fun of(traitAvgs: Map<Trait, Double>): Archetype {
        val sorted = traitAvgs.entries
            .sortedWith(compareByDescending<Map.Entry<Trait, Double>> { it.value }.thenBy { it.key })
        val top1 = sorted[0].key
        val top2 = sorted[1].key
        val lowest = sorted.last().key
        return Archetype(
            name = "${HEAD.getValue(top1)} ${TAIL.getValue(top2)}",
            desc = "이 팀의 강점은 ${label(top1)}과(와) ${label(top2)}. 반대로 ${label(lowest)} 쪽은 팀에서 가장 옅은 기운이에요.",
            riskNote = RISK.getValue(lowest),
        )
    }

    private fun label(t: Trait): String = when (t) {
        Trait.DRIVE -> "추진력"
        Trait.CAUTION -> "신중함"
        Trait.SOCIAL -> "사교성"
        Trait.DETAIL -> "꼼꼼함"
        Trait.CREATIVE -> "창의성"
        Trait.HARMONY -> "조율력"
        Trait.COMMAND -> "리더십"
        Trait.STEADY -> "안정감"
    }
}
