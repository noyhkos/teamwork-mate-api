package dev.teamworkmate.api.domain

import dev.teamworkmate.api.domain.roles.Role
import dev.teamworkmate.api.domain.roles.RoleAssigner
import dev.teamworkmate.api.domain.roles.RoleScorer
import dev.teamworkmate.api.domain.saju.DayStrength
import dev.teamworkmate.api.domain.saju.SajuSummary
import dev.teamworkmate.api.domain.traits.Trait
import dev.teamworkmate.api.domain.traits.TraitVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoleTest {

    private fun vector(vararg highs: Pair<Trait, Int>): TraitVector =
        TraitVector(Trait.entries.associateWith { 50 } + highs.toMap())

    private val plainSaju = SajuSummary.fromRaw(
        dayMasterStem = "丙",
        elementsKo = mapOf("목" to 2, "화" to 2, "토" to 2, "금" to 1, "수" to 1),
        gods = listOf("비견", "식신", "정재", "정관", "정인"),
        strength = DayStrength.NEUTRAL,
        birthTimeKnown = true,
    )

    @Test
    fun `commanding profile scores leader highest`() {
        val scores = RoleScorer.scores(vector(Trait.COMMAND to 95, Trait.DRIVE to 90), plainSaju)
        val best = scores.maxByOrNull { it.value }!!.key
        assertEquals(Role.LEADER, best)
    }

    @Test
    fun `cautious detailed profile scores brake highest`() {
        val scores = RoleScorer.scores(vector(Trait.CAUTION to 95, Trait.DETAIL to 90), plainSaju)
        assertEquals(Role.BRAKE, scores.maxByOrNull { it.value }!!.key)
    }

    @Test
    fun `jaeseong-heavy saju boosts treasurer`() {
        val jaeseongSaju = SajuSummary.fromRaw(
            dayMasterStem = "丙",
            elementsKo = mapOf("목" to 2, "화" to 2, "토" to 2, "금" to 1, "수" to 1),
            gods = listOf("정재", "편재", "정재", "편재"),
            strength = DayStrength.NEUTRAL,
            birthTimeKnown = true,
        )
        val flat = vector()
        val boosted = RoleScorer.scores(flat, jaeseongSaju).getValue(Role.TREASURER)
        val plain = RoleScorer.scores(flat, plainSaju).getValue(Role.TREASURER)
        assertTrue(boosted > plain)
    }

    /** Flat 50s except where a member is meant to stand out. */
    private fun flatScores(vararg highs: Pair<Role, Double>): Map<Role, Double> =
        Role.SPECIALIZED.associateWith { 50.0 } + highs.toMap()

    @Test
    fun `core rungs are filled first, the rest go to best fit`() {
        val scores = mapOf(
            "m1" to flatScores(Role.LEADER to 90.0),
            "m2" to flatScores(Role.STRATEGIST to 88.0),
            "m3" to flatScores(Role.TREASURER to 87.0),
            "m4" to flatScores(Role.BRAKE to 95.0), // highest score of anyone, still not core
        )
        val result = RoleAssigner.assign(scores)

        assertEquals(4, result.size)
        assertEquals(Role.LEADER, result.first { it.memberId == "m1" }.role)
        assertEquals(Role.STRATEGIST, result.first { it.memberId == "m2" }.role)
        assertEquals(Role.TREASURER, result.first { it.memberId == "m3" }.role)
        // m4 was not needed for a core rung, so their own best fit wins
        assertEquals(Role.BRAKE, result.first { it.memberId == "m4" }.role)
        assertTrue(result.none { it.role == Role.MEMBER })
    }

    @Test
    fun `the smallest team still fills every core rung`() {
        // Every member's own best role is something other than a core rung —
        // the old purely greedy pass produced a report with no 리더 at all.
        val scores = mapOf(
            "m1" to flatScores(Role.BRAKE to 95.0),
            "m2" to flatScores(Role.MOOD to 93.0),
            "m3" to flatScores(Role.SCRIBE to 91.0),
        )
        val roles = RoleAssigner.assign(scores).map { it.role }.toSet()

        assertEquals(Role.CORE.toSet(), roles)
    }

    @Test
    fun `core rung goes to the best candidate for it`() {
        val scores = mapOf(
            "m1" to flatScores(Role.LEADER to 60.0),
            "m2" to flatScores(Role.LEADER to 91.0),
            "m3" to flatScores(Role.LEADER to 75.0),
        )
        val result = RoleAssigner.assign(scores)
        assertEquals("m2", result.first { it.role == Role.LEADER }.memberId)
    }

    @Test
    fun `team larger than the ladder puts the rest on the bottom rung`() {
        val identical = flatScores(Role.MOOD to 99.0)
        val ladder = Role.SPECIALIZED.size
        val scores = (1..(ladder + 3)).associate { "m$it" to identical }
        val result = RoleAssigner.assign(scores)

        assertEquals(ladder + 3, result.size)
        assertEquals(Role.SPECIALIZED.toSet(), result.map { it.role }.toSet() - Role.MEMBER)
        // every rung is held exactly once; everyone else is a plain member
        assertEquals(ladder, result.count { it.role != Role.MEMBER })
        assertEquals(3, result.count { it.role == Role.MEMBER })
        // the fallback still carries a real number: the member's best fitness
        assertTrue(result.filter { it.role == Role.MEMBER }.all { it.score == 99.0 })
    }

    @Test
    fun `MEMBER is never handed out while the ladder has rungs left`() {
        // A runaway MEMBER score must not win anything — it has no formula and
        // is excluded from both passes.
        val scores = mapOf(
            "m1" to flatScores(Role.MEMBER to 99.0),
            "m2" to flatScores(Role.MEMBER to 98.0),
        )
        val result = RoleAssigner.assign(scores)
        assertTrue(result.none { it.role == Role.MEMBER })
        assertTrue(result.map { it.role }.all { it in Role.CORE })
    }

    @Test
    fun `assignment is deterministic under ties`() {
        val identical = Role.SPECIALIZED.associateWith { 50.0 }
        val scores = (1..4).associate { "m$it" to identical }
        assertEquals(RoleAssigner.assign(scores), RoleAssigner.assign(scores))
    }
}
