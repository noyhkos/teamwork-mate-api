package dev.teamworkmate.api.analysis

import dev.teamworkmate.api.llm.PhraseService
import dev.teamworkmate.api.queue.QueuePort
import dev.teamworkmate.api.team.MemberService
import dev.teamworkmate.api.team.Team
import dev.teamworkmate.api.team.TeamRepository
import dev.teamworkmate.api.team.TeamStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Owns the analysis state machine and the request/worker boundary.
 * The HTTP thread only validates and enqueues; all heavy work (calc, scoring,
 * LLM phrases) happens on the worker so a request never blocks for minutes.
 */
@Service
class AnalysisJobService(
    private val teams: TeamRepository,
    private val memberService: MemberService,
    private val analysisService: AnalysisService,
    private val phraseService: PhraseService,
    private val state: TeamStateService,
    private val queue: ObjectProvider<QueuePort>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Request path: validate, claim the team, hand off. Returns as soon as it is queued. */
    fun submit(team: Team) {
        val members = memberService.listFor(team.id)
        if (members.size < 2) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "need at least 2 members (have ${members.size})")
        }
        // Committed before the hand-off, so a duplicate submit sees `processing`
        // and the worker never reads a stale row.
        state.claim(team.id)
        queue.getObject().submit(team.id)
    }

    /** Worker path. Never throws — failures are recorded on the team row. */
    fun run(teamId: UUID) {
        val team = teams.findById(teamId).orElse(null)
        if (team == null) {
            log.warn("analysis job for unknown team {}", teamId)
            return
        }
        val now = Instant.now()
        try {
            analysisService.analyze(team, now)
        } catch (e: Exception) {
            log.error("analysis failed for team {}", teamId, e)
            state.markFailed(teamId)
            return
        }
        // Phrases are best-effort, but we wait for them before flipping to `done`
        // so the first render of the report is not half template, half LLM.
        runCatching { phraseService.enrich(team, now) }
            .onFailure { log.warn("phrase enrichment failed for team {}: {}", teamId, it.message) }
        state.markDone(teamId)
    }
}

/**
 * Status writes in their own transactions. Separate bean on purpose: self-invocation
 * inside a single bean bypasses the transactional proxy, which is how a "failed"
 * marker ends up rolled back together with the failure that caused it.
 */
@Service
class TeamStateService(private val teams: TeamRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claim(teamId: UUID) {
        val team = teams.findById(teamId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "team not found")
        }
        if (team.status == TeamStatus.processing) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "analysis already running")
        }
        team.status = TeamStatus.processing
        teams.save(team)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markDone(teamId: UUID) {
        teams.findById(teamId).ifPresent {
            it.status = TeamStatus.done
            teams.save(it)
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(teamId: UUID) {
        teams.findById(teamId).ifPresent {
            it.status = TeamStatus.failed
            teams.save(it)
        }
    }
}
