package dev.teamworkmate.api.analysis

import dev.teamworkmate.api.domain.pairs.PairFactor
import dev.teamworkmate.api.domain.roles.Role
import dev.teamworkmate.api.facts.SajuFactsRepository
import dev.teamworkmate.api.llm.PhrasePrompts
import dev.teamworkmate.api.team.MemberService
import dev.teamworkmate.api.team.Team
import dev.teamworkmate.api.team.TeamRepository
import dev.teamworkmate.api.team.TeamService
import dev.teamworkmate.api.team.TeamStatus
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

data class RoleView(
    val nickname: String, val role: String, val roleKo: String, val score: Double,
    val reason: String, // LLM phrase when accepted, deterministic template otherwise
)
data class PairView(val a: String, val b: String, val total: Int, val factors: List<String>, val reason: String?)

/**
 * Every pair, without the factor list or the phrase. The relationship polygon
 * draws all C(n,2) edges — 66 of them at twelve members — and needs nothing
 * but the score, so the heavy fields stay on bestPair/worstPair.
 */
data class PairEdge(val a: String, val b: String, val total: Int)
data class ReportView(
    val teamName: String,
    val archetype: String,
    val archetypeDesc: String?,
    val intro: String?, // LLM team intro; null falls back to archetypeDesc in the UI
    val harmonyScore: Int,
    val roles: List<RoleView>,
    val bestPair: PairView?,
    val worstPair: PairView?,
    val pairs: List<PairEdge>,
    val traitAvgs: Map<String, Double>,
    val elementTotals: Map<String, Int>,
    val riskNote: String?,
    val samjaeMembers: List<String>,
    val shareSlug: String?,
    val llmPhrases: Boolean,
)

/** 202 body — the work runs on the worker; poll the team view for the terminal state. */
data class AnalyzeAccepted(val status: TeamStatus, val pollUrl: String)

@RestController
@RequestMapping("/api")
class ReportController(
    private val teamService: TeamService,
    private val teams: TeamRepository,
    private val memberService: MemberService,
    private val jobService: AnalysisJobService,
    private val roleScores: RoleScoreRepository,
    private val pairScores: PairScoreRepository,
    private val teamAnalysis: TeamAnalysisRepository,
    private val sajuFacts: SajuFactsRepository,
    private val cardClient: CardClient,
    private val cardCache: CardCache,
    private val om: ObjectMapper,
) {

    @PostMapping("/teams/{token}/analyze")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    fun analyze(@PathVariable token: String): AnalyzeAccepted {
        val team = teamService.byToken(token)
        jobService.submit(team)
        return AnalyzeAccepted(TeamStatus.processing, "/api/teams/$token")
    }

    @GetMapping("/teams/{token}/report")
    fun reportByToken(@PathVariable token: String): ReportView = buildReport(teamService.byToken(token))

    /** Public share link — read-only view by slug. */
    @GetMapping("/reports/{slug}")
    fun reportBySlug(@PathVariable slug: String): ReportView {
        val team = teams.findByShareSlug(slug)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없어요.")
        return buildReport(team)
    }

    /** Share card image for SNS/OG preview. */
    @GetMapping("/reports/{slug}/card.png", produces = [org.springframework.http.MediaType.IMAGE_PNG_VALUE])
    fun cardBySlug(@PathVariable slug: String): org.springframework.http.ResponseEntity<ByteArray> {
        val team = teams.findByShareSlug(slug)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없어요.")
        // One row by primary key, versus rebuilding the report and calling calc:
        // reading analyzedAt first is what lets the cache below be correct.
        val analyzedAt = teamAnalysis.findById(team.id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없어요.") }
            .analyzedAt
        val png = cardCache.get(slug, analyzedAt) { cardClient.render(buildReport(team)) }
        return org.springframework.http.ResponseEntity.ok()
            .header("Cache-Control", "public, max-age=3600")
            .body(png)
    }

    private fun buildReport(team: Team): ReportView {
        if (team.status != TeamStatus.done) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "아직 리포트가 없어요.")
        }
        val analysis = teamAnalysis.findById(team.id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없어요.") }

        val members = memberService.listFor(team.id)
        val nickname: Map<UUID, String> = members.associate { it.id to it.nickname }

        val assignedRoles = roleScores.findByTeamIdAndAssignedTrue(team.id)
        val roles = assignedRoles
            .sortedBy { rs -> Role.entries.first { it.key == rs.role }.ordinal }
            .map { rs ->
                val role = Role.entries.first { it.key == rs.role }
                RoleView(
                    nickname.getValue(rs.memberId), role.key, role.ko, rs.score,
                    reason = rs.reason ?: PhrasePrompts.templateRoleReason(role, rs.score),
                )
            }

        val pairs = pairScores.findByTeamIdOrderByTotalDescIdAsc(team.id)
        val best = pairs.firstOrNull()?.toView(nickname)
        val worst = pairs.lastOrNull()?.takeIf { pairs.size > 1 }?.toView(nickname)
        val llmPhrases = assignedRoles.any { it.reason != null } ||
            pairs.any { it.reason != null } || analysis.intro != null

        val samjaeMembers = members.filter { m ->
            sajuFacts.findById(m.id).map { f ->
                f.samjae?.let { om.readTree(it).path("inSamjae").booleanValue() } ?: false
            }.orElse(false)
        }.map { it.nickname }

        @Suppress("UNCHECKED_CAST")
        return ReportView(
            teamName = team.name,
            archetype = analysis.archetypeName,
            archetypeDesc = analysis.archetypeDesc,
            intro = analysis.intro,
            harmonyScore = analysis.harmonyScore,
            roles = roles,
            bestPair = best,
            worstPair = worst,
            pairs = pairs.map {
                PairEdge(nickname.getValue(it.memberAId), nickname.getValue(it.memberBId), it.total)
            },
            traitAvgs = om.readValue(analysis.traitAvgs, Map::class.java) as Map<String, Double>,
            elementTotals = om.readValue(analysis.elementTotals, Map::class.java) as Map<String, Int>,
            riskNote = analysis.riskNote,
            samjaeMembers = samjaeMembers,
            shareSlug = team.shareSlug,
            llmPhrases = llmPhrases,
        )
    }

    private fun PairScoreEntity.toView(nickname: Map<UUID, String>): PairView {
        val labels = om.readValue(factors, Array<PairFactor>::class.java).map { it.label }
        return PairView(nickname.getValue(memberAId), nickname.getValue(memberBId), total, labels, reason)
    }
}
