package dev.teamworkmate.api.domain.roles

/**
 * Declaration order is the ladder the report renders top-down.
 *
 * `core` roles are filled first and unconditionally, so a team always learns
 * who runs it — the greedy pass alone left small teams with no 리더 at all.
 * Everyone else is matched to whichever remaining role fits them best.
 *
 * MEMBER is the bottom rung: the only role more than one person can hold, and
 * the only one without a fitness formula. It is excluded from scoring and from
 * the matching pass.
 *
 * key matches the wire/DB representation.
 */
enum class Role(val key: String, val ko: String, val core: Boolean = false) {
    LEADER("leader", "리더", core = true),
    STRATEGIST("strategist", "책략가", core = true),
    TREASURER("treasurer", "총무"),
    FIXER("fixer", "해결사"),
    ENFORCER("enforcer", "군기반장"),
    IDEA("idea", "아이디어뱅크"),
    DIPLOMAT("diplomat", "외교관"),
    MEDIATOR("mediator", "조율가"),
    MOOD("mood", "분위기메이커"),
    SCRIBE("scribe", "기록가"),
    BRAKE("brake", "브레이크"),
    MEMBER("member", "일반 멤버"),
    ;

    companion object {
        /** The roles a fitness score exists for — everything but the fallback rung. */
        val SPECIALIZED: List<Role> = entries - MEMBER

        /** Always filled, in this order, before anything is matched on score. */
        val CORE: List<Role> = SPECIALIZED.filter { it.core }
    }
}
