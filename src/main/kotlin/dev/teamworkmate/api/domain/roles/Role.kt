package dev.teamworkmate.api.domain.roles

/** key matches the wire/DB representation. */
enum class Role(val key: String, val ko: String) {
    LEADER("leader", "리더"),
    VICE("vice", "부리더"),
    TREASURER("treasurer", "총무"),
    MOOD("mood", "분위기메이커"),
    IDEA("idea", "아이디어뱅크"),
    MEDIATOR("mediator", "조율가"),
    BRAKE("brake", "브레이크"),
}
