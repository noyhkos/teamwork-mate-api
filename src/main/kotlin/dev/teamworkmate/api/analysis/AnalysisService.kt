package dev.teamworkmate.api.analysis

import dev.teamworkmate.api.domain.mbti.Mbti
import dev.teamworkmate.api.domain.pairs.PairScorer
import dev.teamworkmate.api.domain.roles.Role
import dev.teamworkmate.api.domain.roles.RoleAssigner
import dev.teamworkmate.api.domain.roles.RoleScorer
import dev.teamworkmate.api.domain.traits.Trait
import dev.teamworkmate.api.domain.traits.TraitEngine
import dev.teamworkmate.api.facts.SajuFactsService
import dev.teamworkmate.api.team.Member
import dev.teamworkmate.api.team.MemberService
import dev.teamworkmate.api.team.Team
import dev.teamworkmate.api.team.TeamRepository
import dev.teamworkmate.api.team.TeamStatus
import dev.teamworkmate.api.team.TokenGenerator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Service
class AnalysisService(
    private val teams: TeamRepository,
    private val memberService: MemberService,
    private val sajuFactsService: SajuFactsService,
    private val roleScores: RoleScoreRepository,
    private val pairScores: PairScoreRepository,
    private val teamAnalysis: TeamAnalysisRepository,
    private val om: ObjectMapper,
) {

    data class Summary(val members: Int, val pairs: Int, val archetype: String, val harmony: Int, val shareSlug: String)

    /**
     * Deterministic pipeline: facts -> traits -> roles -> pairs -> team analysis.
     * Called by the queue worker; AnalysisJobService owns the state machine, so this
     * method assumes the team is already claimed and only reports the terminal state.
     */
    @Transactional
    fun analyze(team: Team, now: Instant): Summary {
        val members = memberService.listFor(team.id)
        if (members.size < 2) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "need at least 2 members (have ${members.size})")
        }

        run {
            val profiles = members.map { member ->
                val facts = sajuFactsService.computeAndStore(member, now)
                val summary = SajuSummaryFactory.fromFacts(facts, om)
                val mbti = Mbti.parse(member.mbti)
                MemberProfile(member, summary, TraitEngine.combined(summary, mbti), mbti)
            }

            // roles
            val scoresByMember = profiles.associate { p ->
                p.member.id.toString() to RoleScorer.scores(p.traits, p.saju)
            }
            val assignments = RoleAssigner.assign(scoresByMember)
            val assignedByMember = assignments.associateBy { it.memberId }

            roleScores.deleteByTeamId(team.id)
            roleScores.saveAll(
                scoresByMember.flatMap { (memberId, scores) ->
                    val assignment = assignedByMember.getValue(memberId)
                    val rows = scores.map { (role, score) ->
                        RoleScoreEntity(
                            teamId = team.id,
                            memberId = UUID.fromString(memberId),
                            role = role.key,
                            score = score,
                            assigned = assignment.role == role,
                        )
                    }
                    // MEMBER carries no fitness formula, so it only reaches the
                    // table for the people who actually land on it.
                    if (assignment.role != Role.MEMBER) rows
                    else rows + RoleScoreEntity(
                        teamId = team.id,
                        memberId = UUID.fromString(memberId),
                        role = Role.MEMBER.key,
                        score = assignment.score,
                        assigned = true,
                    )
                },
            )

            // pairs — canonical order matches the DB CHECK (uuid byte order == hex-string order)
            pairScores.deleteByTeamId(team.id)
            val pairEntities = mutableListOf<PairScoreEntity>()
            for (i in profiles.indices) {
                for (j in i + 1 until profiles.size) {
                    val (first, second) =
                        if (profiles[i].member.id.toString() < profiles[j].member.id.toString()) profiles[i] to profiles[j]
                        else profiles[j] to profiles[i]
                    val score = PairScorer.score(first.saju, first.mbti, second.saju, second.mbti)
                    pairEntities += PairScoreEntity(
                        teamId = team.id,
                        memberAId = first.member.id,
                        memberBId = second.member.id,
                        total = score.total,
                        sajuScore = score.sajuScore,
                        mbtiScore = score.mbtiScore,
                        factors = om.writeValueAsString(score.factors),
                    )
                }
            }
            pairScores.saveAll(pairEntities)

            // team level
            val traitAvgs = Trait.entries.associateWith { t -> profiles.map { it.traits[t] }.average() }
            val archetype = ArchetypeNamer.of(traitAvgs)
            val harmony = harmonyScore(pairEntities.map { it.total })
            val elementTotals = mapOf(
                "목" to profiles.sumOf { it.saju.elementCounts.getOrDefault(dev.teamworkmate.api.domain.saju.Element.WOOD, 0) },
                "화" to profiles.sumOf { it.saju.elementCounts.getOrDefault(dev.teamworkmate.api.domain.saju.Element.FIRE, 0) },
                "토" to profiles.sumOf { it.saju.elementCounts.getOrDefault(dev.teamworkmate.api.domain.saju.Element.EARTH, 0) },
                "금" to profiles.sumOf { it.saju.elementCounts.getOrDefault(dev.teamworkmate.api.domain.saju.Element.METAL, 0) },
                "수" to profiles.sumOf { it.saju.elementCounts.getOrDefault(dev.teamworkmate.api.domain.saju.Element.WATER, 0) },
            )

            teamAnalysis.save(
                TeamAnalysisEntity(
                    teamId = team.id,
                    archetypeName = archetype.name,
                    archetypeDesc = archetype.desc,
                    harmonyScore = harmony,
                    traitAvgs = om.writeValueAsString(traitAvgs.mapKeys { it.key.name }),
                    elementTotals = om.writeValueAsString(elementTotals),
                    riskNote = archetype.riskNote,
                    analyzedAt = now,
                ),
            )

            // The terminal status is set by AnalysisJobService once the phrase layer
            // has also run — otherwise the first view of the report is half-written.
            if (team.shareSlug == null) team.shareSlug = TokenGenerator.generate(12)
            teams.save(team)

            return Summary(profiles.size, pairEntities.size, archetype.name, harmony, team.shareSlug!!)
        }
    }

    /** Average pair total minus a spread penalty — one explosive pair drags the vibe. */
    private fun harmonyScore(totals: List<Int>): Int {
        if (totals.isEmpty()) return 50
        val avg = totals.average()
        val variance = totals.sumOf { (it - avg) * (it - avg) } / totals.size
        return (avg - sqrt(variance) / 2).roundToInt().coerceIn(0, 100)
    }

    private data class MemberProfile(
        val member: Member,
        val saju: dev.teamworkmate.api.domain.saju.SajuSummary,
        val traits: dev.teamworkmate.api.domain.traits.TraitVector,
        val mbti: Mbti,
    )
}
