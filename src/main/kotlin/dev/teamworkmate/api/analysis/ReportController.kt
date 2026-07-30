package dev.teamworkmate.api.analysis

import dev.teamworkmate.api.domain.pairs.PairFactor
import dev.teamworkmate.api.domain.roles.Role
import dev.teamworkmate.api.facts.SajuFactsRepository
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

data class RoleView(val nickname: String, val role: String, val roleKo: String, val score: Double, val unique: Boolean?)
data class PairView(val a: String, val b: String, val total: Int, val factors: List<String>)
data class ReportView(
    val teamName: String?,
    val archetype: String,
    val archetypeDesc: String?,
    val harmonyScore: Int,
    val roles: List<RoleView>,
    val bestPair: PairView?,
    val worstPair: PairView?,
    val traitAvgs: Map<String, Double>,
    val elementTotals: Map<String, Int>,
    val riskNote: String?,
    val samjaeMembers: List<String>,
    val shareSlug: String?,
)

@RestController
@RequestMapping("/api")
class ReportController(
    private val teamService: TeamService,
    private val teams: TeamRepository,
    private val memberService: MemberService,
    private val analysisService: AnalysisService,
    private val roleScores: RoleScoreRepository,
    private val pairScores: PairScoreRepository,
    private val teamAnalysis: TeamAnalysisRepository,
    private val sajuFacts: SajuFactsRepository,
    private val cardClient: CardClient,
    private val om: ObjectMapper,
) {

    @PostMapping("/teams/admin/{token}/analyze")
    fun analyze(@PathVariable token: String): AnalysisService.Summary =
        analysisService.analyze(teamService.byAdminToken(token), Instant.now())

    @GetMapping("/teams/invite/{token}/report")
    fun reportByInvite(@PathVariable token: String): ReportView = buildReport(teamService.byInviteToken(token))

    /** Public share link — read-only view by slug. */
    @GetMapping("/reports/{slug}")
    fun reportBySlug(@PathVariable slug: String): ReportView {
        val team = teams.findByShareSlug(slug)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "report not found")
        return buildReport(team)
    }

    /** Share card image for SNS/OG preview. */
    @GetMapping("/reports/{slug}/card.png", produces = [org.springframework.http.MediaType.IMAGE_PNG_VALUE])
    fun cardBySlug(@PathVariable slug: String): org.springframework.http.ResponseEntity<ByteArray> {
        val team = teams.findByShareSlug(slug)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "report not found")
        val png = cardClient.render(buildReport(team))
        return org.springframework.http.ResponseEntity.ok()
            .header("Cache-Control", "public, max-age=3600")
            .body(png)
    }

    private fun buildReport(team: Team): ReportView {
        if (team.status != TeamStatus.done) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "report not ready (status=${team.status})")
        }
        val analysis = teamAnalysis.findById(team.id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "report not found") }

        val members = memberService.listFor(team.id)
        val nickname: Map<UUID, String> = members.associate { it.id to it.nickname }

        val roles = roleScores.findByTeamIdAndAssignedTrue(team.id)
            .sortedBy { rs -> Role.entries.first { it.key == rs.role }.ordinal }
            .map { rs ->
                val role = Role.entries.first { it.key == rs.role }
                RoleView(nickname.getValue(rs.memberId), role.key, role.ko, rs.score, rs.assignedUnique)
            }

        val pairs = pairScores.findByTeamIdOrderByTotalDesc(team.id)
        val best = pairs.firstOrNull()?.toView(nickname)
        val worst = pairs.lastOrNull()?.takeIf { pairs.size > 1 }?.toView(nickname)

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
            harmonyScore = analysis.harmonyScore,
            roles = roles,
            bestPair = best,
            worstPair = worst,
            traitAvgs = om.readValue(analysis.traitAvgs, Map::class.java) as Map<String, Double>,
            elementTotals = om.readValue(analysis.elementTotals, Map::class.java) as Map<String, Int>,
            riskNote = analysis.riskNote,
            samjaeMembers = samjaeMembers,
            shareSlug = team.shareSlug,
        )
    }

    private fun PairScoreEntity.toView(nickname: Map<UUID, String>): PairView {
        val labels = om.readValue(factors, Array<PairFactor>::class.java).map { it.label }
        return PairView(nickname.getValue(memberAId), nickname.getValue(memberBId), total, labels)
    }
}
