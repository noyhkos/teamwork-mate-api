package dev.teamworkmate.api.domain.roles

data class RoleAssignment(
    val memberId: String,
    val role: Role,
    val score: Double, // for MEMBER this is the member's best specialized fitness
)

/**
 * Two passes, then a fallback.
 *
 * 1. **Core rungs, unconditionally.** LEADER and STRATEGIST go to the best
 *    remaining candidate for each, in ladder order. A purely greedy pass used
 *    to leave small teams with no 리더 at all — every member's best-fitting
 *    role simply happened to be something else — which reads as a broken
 *    report rather than an honest one.
 * 2. **Everyone else by best fit.** The remaining members and roles are matched
 *    greedily on the highest (member, role) score, one role per person.
 * 3. **MEMBER** takes whoever is left once the ladder runs out, carrying their
 *    best specialized score.
 *
 * Ties break by memberId then role ordinal, so results are fully deterministic.
 */
object RoleAssigner {

    fun assign(scoresByMember: Map<String, Map<Role, Double>>): List<RoleAssignment> {
        val takenMembers = mutableSetOf<String>()
        val takenRoles = mutableSetOf<Role>()
        val result = mutableListOf<RoleAssignment>()

        fun scoreOf(member: String, role: Role) = scoresByMember[member]?.get(role) ?: 0.0

        for (role in Role.CORE) {
            val best = scoresByMember.keys
                .filter { it !in takenMembers }
                .sortedWith(compareByDescending<String> { scoreOf(it, role) }.thenBy { it })
                .firstOrNull() ?: break
            result += RoleAssignment(best, role, scoreOf(best, role))
            takenMembers += best
            takenRoles += role
        }

        val candidates = scoresByMember
            .filterKeys { it !in takenMembers }
            .flatMap { (m, rs) ->
                rs.filterKeys { it != Role.MEMBER && it !in takenRoles }.map { (r, s) -> Triple(m, r, s) }
            }
            .sortedWith(
                compareByDescending<Triple<String, Role, Double>> { it.third }
                    .thenBy { it.first }
                    .thenBy { it.second.ordinal },
            )

        for ((member, role, score) in candidates) {
            if (member in takenMembers || role in takenRoles) continue
            result += RoleAssignment(member, role, score)
            takenMembers += member
            takenRoles += role
        }

        for ((member, roleScores) in scoresByMember) {
            if (member in takenMembers) continue
            val best = roleScores.filterKeys { it != Role.MEMBER }.values.maxOrNull() ?: 0.0
            result += RoleAssignment(member, Role.MEMBER, best)
            takenMembers += member
        }

        return result.sortedBy { it.memberId }
    }
}
