package dev.teamworkmate.api.domain.traits

enum class Trait {
    DRIVE, // 추진력
    CAUTION, // 신중함
    SOCIAL, // 사교성
    DETAIL, // 꼼꼼함
    CREATIVE, // 창의성
    HARMONY, // 조율력
    COMMAND, // 리더십
    STEADY, // 안정감
}

/** 0..100 per trait. Deterministic value object — same input, same vector. */
data class TraitVector(val scores: Map<Trait, Int>) {
    init {
        require(scores.keys.containsAll(Trait.entries)) { "all traits required" }
    }

    operator fun get(trait: Trait): Int = scores.getValue(trait)

    fun top(n: Int): List<Trait> =
        scores.entries.sortedWith(compareByDescending<Map.Entry<Trait, Int>> { it.value }.thenBy { it.key })
            .take(n).map { it.key }

    companion object {
        fun of(raw: Map<Trait, Double>): TraitVector =
            TraitVector(Trait.entries.associateWith { t ->
                raw.getOrDefault(t, 0.0).coerceIn(0.0, 100.0).let { Math.round(it).toInt() }
            })
    }
}
