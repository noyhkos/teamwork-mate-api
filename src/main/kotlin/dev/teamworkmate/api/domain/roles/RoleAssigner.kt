package dev.teamworkmate.api.domain.roles

data class RoleAssignment(
    val memberId: String,
    val role: Role,
    val score: Double,
    val unique: Boolean, // false when assigned in the duplicate round (team larger than role pool)
)

/**
 * Greedy global assignment: highest (member, role) score first, one role per member,
 * roles unique while they last. Leftover members get their personal best role (duplicates flagged).
 * Ties break by memberId then role ordinal so results are fully deterministic.
 */
object RoleAssigner {

    fun assign(scoresByMember: Map<String, Map<Role, Double>>): List<RoleAssignment> {
        val triples = scoresByMember
            .flatMap { (m, rs) -> rs.map { (r, s) -> Triple(m, r, s) } }
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
            result += RoleAssignment(member, role, score, unique = true)
            assignedMembers += member
            assignedRoles += role
        }

        for ((member, roleScores) in scoresByMember) {
            if (member in assignedMembers) continue
            val best = roleScores.entries
                .sortedWith(compareByDescending<Map.Entry<Role, Double>> { it.value }.thenBy { it.key.ordinal })
                .first()
            result += RoleAssignment(member, best.key, best.value, unique = false)
            assignedMembers += member
        }

        return result.sortedBy { it.memberId }
    }
}
