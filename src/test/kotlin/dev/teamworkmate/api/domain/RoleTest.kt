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

    @Test
    fun `assignment gives everyone exactly one role, unique while roles last`() {
        val scores = mapOf(
            "m1" to mapOf(Role.LEADER to 90.0, Role.MOOD to 40.0, Role.BRAKE to 30.0),
            "m2" to mapOf(Role.LEADER to 80.0, Role.MOOD to 85.0, Role.BRAKE to 20.0),
            "m3" to mapOf(Role.LEADER to 70.0, Role.MOOD to 30.0, Role.BRAKE to 88.0),
        )
        val result = RoleAssigner.assign(scores)
        assertEquals(3, result.size)
        assertEquals(setOf(Role.LEADER, Role.MOOD, Role.BRAKE), result.map { it.role }.toSet())
        assertEquals(Role.LEADER, result.first { it.memberId == "m1" }.role)
        assertEquals(Role.MOOD, result.first { it.memberId == "m2" }.role)
        assertEquals(Role.BRAKE, result.first { it.memberId == "m3" }.role)
        assertTrue(result.none { it.role == Role.MEMBER })
    }

    @Test
    fun `team larger than the ladder puts the rest on the bottom rung`() {
        val identical = mapOf(
            Role.MOOD to 99.0, Role.LEADER to 80.0, Role.VICE to 70.0, Role.TREASURER to 60.0,
            Role.IDEA to 50.0, Role.MEDIATOR to 40.0, Role.BRAKE to 30.0,
        )
        val scores = (1..10).associate { "m$it" to identical }
        val result = RoleAssigner.assign(scores)

        assertEquals(10, result.size)
        assertEquals(Role.SPECIALIZED.toSet(), result.map { it.role }.toSet() - Role.MEMBER)
        // seven rungs are held once each; everyone else is a plain member
        assertEquals(7, result.count { it.role != Role.MEMBER })
        assertEquals(3, result.count { it.role == Role.MEMBER })
        // the fallback still carries a real number: the member's best fitness
        assertTrue(result.filter { it.role == Role.MEMBER }.all { it.score == 99.0 })
    }

    @Test
    fun `MEMBER never wins the unique round even when scored`() {
        val scores = mapOf(
            "m1" to mapOf(Role.MEMBER to 99.0, Role.LEADER to 10.0),
            "m2" to mapOf(Role.MEMBER to 98.0, Role.MOOD to 9.0),
        )
        val result = RoleAssigner.assign(scores)
        assertEquals(setOf(Role.LEADER, Role.MOOD), result.map { it.role }.toSet())
    }

    @Test
    fun `assignment is deterministic under ties`() {
        val identical = Role.SPECIALIZED.associateWith { 50.0 }
        val scores = (1..4).associate { "m$it" to identical }
        assertEquals(RoleAssigner.assign(scores), RoleAssigner.assign(scores))
    }
}
