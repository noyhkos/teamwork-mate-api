package dev.teamworkmate.api.domain.roles

/**
 * Declaration order is the ladder the report renders top-down. MEMBER is the
 * bottom rung and the only role more than one person can hold — it has no
 * fitness formula, so it is excluded from scoring and from the unique round.
 * key matches the wire/DB representation.
 */
enum class Role(val key: String, val ko: String) {
    LEADER("leader", "리더"),
    VICE("vice", "부리더"),
    TREASURER("treasurer", "총무"),
    MOOD("mood", "분위기메이커"),
    IDEA("idea", "아이디어뱅크"),
    MEDIATOR("mediator", "조율가"),
    BRAKE("brake", "브레이크"),
    MEMBER("member", "일반 멤버"),
    ;

    companion object {
        /** The roles a fitness score exists for — everything but the fallback rung. */
        val SPECIALIZED: List<Role> = entries - MEMBER
    }
}
