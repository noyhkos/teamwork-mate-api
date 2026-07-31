package dev.teamworkmate.api.domain.roles

data class RoleAssignment(
    val memberId: String,
    val role: Role,
    val score: Double, // for MEMBER this is the member's best specialized fitness
)

/**
 * Greedy global assignment: highest (member, role) score first, one role per
 * member, each specialized role held by exactly one person. Whoever is left
 * once the seven rungs are taken becomes a plain MEMBER — the team is larger
 * than the ladder, not the person a worse fit.
 * Ties break by memberId then role ordinal so results are fully deterministic.
 */
object RoleAssigner {

    fun assign(scoresByMember: Map<String, Map<Role, Double>>): List<RoleAssignment> {
        val triples = scoresByMember
            .flatMap { (m, rs) -> rs.filterKeys { it != Role.MEMBER }.map { (r, s) -> Triple(m, r, s) } }
            .sortedWith(
                compareByDescending<Triple<String, Role, Double>> { it.third }
                    .thenBy { it.first }
                    .thenBy { it.second.ordinal },
            )

        val assignedMembers = mutableSetOf<String>()
        val assignedRoles = mutableSetOf<Role>()
        val result = mutableListOf<RoleAssignment>()

        for ((member, role, score) in triples) {
            if (member in assignedMembers || role in assignedRoles) continue
            result += RoleAssignment(member, role, score)
            assignedMembers += member
            assignedRoles += role
        }

        for ((member, roleScores) in scoresByMember) {
            if (member in assignedMembers) continue
            val best = roleScores.filterKeys { it != Role.MEMBER }.values.maxOrNull() ?: 0.0
            result += RoleAssignment(member, Role.MEMBER, best)
            assignedMembers += member
        }

        return result.sortedBy { it.memberId }
    }
}
