package dev.teamworkmate.api.llm

import dev.teamworkmate.api.analysis.PairScoreRepository
import dev.teamworkmate.api.analysis.RoleScoreRepository
import dev.teamworkmate.api.analysis.SajuSummaryFactory
import dev.teamworkmate.api.analysis.TeamAnalysisRepository
import dev.teamworkmate.api.domain.mbti.Mbti
import dev.teamworkmate.api.domain.roles.Role
import dev.teamworkmate.api.domain.traits.TraitEngine
import dev.teamworkmate.api.facts.SajuFacts
import dev.teamworkmate.api.facts.SajuFactsRepository
import dev.teamworkmate.api.team.MemberService
import dev.teamworkmate.api.team.Team
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

/**
 * Best-effort phrase layer on top of the deterministic pipeline:
 * grounding -> generate -> judge (faithfulness) -> persist. Text is served only
 * when the judge confirms every claim; otherwise the report falls back to templates.
 * Failures here never fail the analysis itself.
 */
@Service
class PhraseService(
    private val llm: LlmPort,
    private val memberService: MemberService,
    private val sajuFactsRepo: SajuFactsRepository,
    private val roleScores: RoleScoreRepository,
    private val pairScores: PairScoreRepository,
    private val teamAnalysis: TeamAnalysisRepository,
    private val generations: LlmGenerationRepository,
    private val evals: EvalResultRepository,
    private val om: ObjectMapper,
    @Value("\${llm.generation-model:gemini-3.6-flash}") private val genModel: String,
    @Value("\${llm.judge-model:gemini-3.5-flash-lite}") private val judgeModel: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_ATTEMPTS = 2
        private const val MAX_PHRASE_LEN = 240
        private const val MAX_INTRO_LEN = 300
    }

    /** Returns llm | partial | failed | skipped — informational only. */
    fun enrich(team: Team, now: Instant): String {
        if (!llm.enabled) return "skipped"
        return try {
            doEnrich(team, now)
        } catch (e: Exception) {
            log.warn("phrase enrichment failed for team {}: {}", team.id, e.message)
            "failed"
        }
    }

    private fun doEnrich(team: Team, now: Instant): String {
        val analysis = teamAnalysis.findById(team.id).orElse(null) ?: return "failed"
        val members = memberService.listFor(team.id)
        val nicknameOf: Map<UUID, String> = members.associate { it.id to it.nickname }
        val factsOf: Map<UUID, SajuFacts> = sajuFactsRepo.findAllById(members.map { it.id }).associateBy { it.memberId }
        val assigned = roleScores.findByTeamIdAndAssignedTrue(team.id)
        val pairs = pairScores.findByTeamIdOrderByTotalDescIdAsc(team.id)

        // --- grounding: only facts that actually drove the scores ---
        val traitsOf = members.associate { m ->
            val facts = factsOf.getValue(m.id)
            m.id to TraitEngine.combined(SajuSummaryFactory.fromFacts(facts, om), Mbti.parse(m.mbti))
        }

        val roleGrounding = assigned
            .sortedBy { rs -> Role.entries.first { it.key == rs.role }.ordinal }
            .map { rs ->
                val m = members.first { it.id == rs.memberId }
                val facts = factsOf.getValue(m.id)
                val role = Role.entries.first { it.key == rs.role }
                val traits = traitsOf.getValue(m.id)
                PhrasePrompts.RoleGroundingMember(
                    nickname = m.nickname,
                    mbti = m.mbti,
                    dayMaster = PhrasePrompts.dayMasterLabel(facts.dayMaster),
                    personaKeywords = StemLexicon.of(facts.dayMaster).keywords,
                    elements = mapOf(
                        "목" to facts.woodCnt, "화" to facts.fireCnt, "토" to facts.earthCnt,
                        "금" to facts.metalCnt, "수" to facts.waterCnt,
                    ),
                    strength = PhrasePrompts.strengthLabel(
                        facts.dayStrength?.let { om.readTree(it).path("strength").stringValue() },
                    ),
                    topTraits = traits.top(2).map { t -> "${PhrasePrompts.TRAIT_KO.getValue(t)} ${traits[t]}" },
                    role = role.ko,
                    roleScore = Math.round(rs.score).toInt(),
                    roleBasis = PhrasePrompts.ROLE_BASIS.getValue(role),
                    inSamjae = facts.samjae?.let { om.readTree(it).path("inSamjae").booleanValue() } ?: false,
                )
            }

        val best = pairs.firstOrNull()
        val worst = pairs.lastOrNull()?.takeIf { pairs.size > 1 }
        val pairGrounding = listOfNotNull(
            best?.let { pairGroundingOf("best", it, nicknameOf, factsOf, members) },
            worst?.let { pairGroundingOf("worst", it, nicknameOf, factsOf, members) },
        )

        val traitAvgs: Map<String, Double> = om.readValue(analysis.traitAvgs, Map::class.java)
            .entries.associate { (k, v) -> k.toString() to (v as Number).toDouble() }
        val sortedAvgs = traitAvgs.entries.sortedByDescending { it.value }
        val koOf = { name: String -> PhrasePrompts.TRAIT_KO.entries.first { it.key.name == name }.value }
        val teamGrounding = PhrasePrompts.TeamGrounding(
            teamName = team.name,
            memberCount = members.size,
            archetype = analysis.archetypeName,
            topTraits = sortedAvgs.take(2).map { "${koOf(it.key)} ${Math.round(it.value)}" },
            lowestTrait = koOf(sortedAvgs.last().key),
            harmonyScore = analysis.harmonyScore,
            elementTotals = om.readValue(analysis.elementTotals, Map::class.java)
                .entries.associate { (k, v) -> k.toString() to (v as Number).toInt() },
        )

        // --- generate -> judge -> apply, per kind ---
        val rolesOk = runKind(team.id, "role_reasons", roleGrounding, PhrasePrompts.ROLE_REASONS_SCHEMA, now,
            { g -> PhrasePrompts.roleReasonsPrompt(g) }) { parsed ->
            val texts = parsed.path("reasons").values()
                .associate { it.path("nickname").stringValue("") to it.path("text").stringValue("") }
            val byNickname = assigned.associateBy { nicknameOf.getValue(it.memberId) }
            val complete = byNickname.keys.all { usable(texts[it], MAX_PHRASE_LEN) }
            // 0 rows updated = a concurrent rerun replaced these rows; treat as not applied.
            complete && byNickname.all { (nick, entity) ->
                roleScores.updateReason(entity.id, texts.getValue(nick)) == 1
            }
        }

        val pairsOk = pairGrounding.isNotEmpty() && runKind(
            team.id, "pair_chemistry", pairGrounding, PhrasePrompts.PAIRS_SCHEMA, now,
            { g -> PhrasePrompts.pairChemistryPrompt(g) }) { parsed ->
            val texts = parsed.path("pairs").values()
                .associate { it.path("key").stringValue("") to it.path("text").stringValue("") }
            val targets = listOfNotNull(best?.let { "best" to it }, worst?.let { "worst" to it })
            val complete = targets.all { (key, _) -> usable(texts[key], MAX_PHRASE_LEN) }
            complete && targets.all { (key, entity) ->
                pairScores.updateReason(entity.id, texts.getValue(key)) == 1
            }
        }

        val introOk = runKind(team.id, "team_intro", teamGrounding, PhrasePrompts.TEAM_INTRO_SCHEMA, now,
            { g -> PhrasePrompts.teamIntroPrompt(g) }) { parsed ->
            val text = parsed.path("text").stringValue("")
            usable(text, MAX_INTRO_LEN) &&
                teamAnalysis.updateIntro(team.id, text, analysis.analyzedAt) == 1
        }

        val accepted = listOf(rolesOk, pairsOk, introOk).count { it }
        return when (accepted) {
            3 -> "llm"
            0 -> "failed"
            else -> "partial"
        }
    }

    private fun usable(text: String?, maxLen: Int): Boolean = !text.isNullOrBlank() && text.length <= maxLen

    private fun pairGroundingOf(
        key: String,
        pair: dev.teamworkmate.api.analysis.PairScoreEntity,
        nicknameOf: Map<UUID, String>,
        factsOf: Map<UUID, SajuFacts>,
        members: List<dev.teamworkmate.api.team.Member>,
    ): PhrasePrompts.PairGrounding {
        val factorLabels = om.readTree(pair.factors).values().map { f ->
            val delta = f.path("delta").doubleValue()
            val sign = if (delta >= 0) "+" else ""
            "${f.path("label").stringValue()} ($sign${Math.round(delta)})"
        }
        return PhrasePrompts.PairGrounding(
            key = key,
            a = nicknameOf.getValue(pair.memberAId),
            b = nicknameOf.getValue(pair.memberBId),
            aDayMaster = PhrasePrompts.dayMasterLabel(factsOf.getValue(pair.memberAId).dayMaster),
            bDayMaster = PhrasePrompts.dayMasterLabel(factsOf.getValue(pair.memberBId).dayMaster),
            aMbti = members.first { it.id == pair.memberAId }.mbti,
            bMbti = members.first { it.id == pair.memberBId }.mbti,
            total = pair.total,
            factors = factorLabels,
        )
    }

    /**
     * One phrase kind: up to MAX_ATTEMPTS of generate -> judge. Text is applied only
     * after a faithful verdict; a judge-transport failure means we never serve the text.
     */
    private fun runKind(
        teamId: UUID,
        kind: String,
        grounding: Any,
        schema: String,
        now: Instant,
        promptOf: (String) -> String,
        apply: (JsonNode) -> Boolean,
    ): Boolean {
        val groundingJson = om.writeValueAsString(grounding)
        var feedback: String? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            val prompt = promptOf(groundingJson) + (feedback?.let {
                "\n\n[재시도 피드백] 직전 시도의 다음 주장이 근거에 없어 탈락했다. 근거 안의 사실만 사용해 다시 써라: $it"
            } ?: "")

            val genStart = System.nanoTime()
            val output = try {
                llm.complete(genModel, prompt, schema)
            } catch (e: Exception) {
                log.warn("llm generation failed (team={}, kind={}, attempt={}): {}", teamId, kind, attempt, e.message)
                saveGeneration(teamId, kind, groundingJson, prompt, null, "failed", attempt, msSince(genStart), now)
                continue
            }
            val genMs = msSince(genStart)

            val parsed = try {
                om.readTree(output)
            } catch (e: Exception) {
                saveGeneration(teamId, kind, groundingJson, prompt, output, "failed", attempt, genMs, now)
                continue
            }

            val judgeStart = System.nanoTime()
            val judge = try {
                om.readTree(llm.complete(judgeModel, PhrasePrompts.judgePrompt(groundingJson, output), PhrasePrompts.JUDGE_SCHEMA))
            } catch (e: Exception) {
                // Unverified text is never served — reject and stop burning quota this round.
                log.warn("judge failed (team={}, kind={}): {}", teamId, kind, e.message)
                saveGeneration(teamId, kind, groundingJson, prompt, output, "rejected", attempt, genMs, now)
                return false
            }
            val judgeMs = msSince(judgeStart)
            // Everything below reads model-controlled JSON — a malformed shape must not
            // escape and drop the audit row for the call we already paid for.
            try {
                val faithful = judge.path("faithful").booleanValue(false)

                if (!faithful) {
                    val gen = saveGeneration(teamId, kind, groundingJson, prompt, output, "rejected", attempt, genMs, now)
                    saveEval(gen.id, judge, faithful = false, judgeMs, now)
                    feedback = judge.path("claims").values()
                        .filter { it.path("verdict").stringValue("") == "unsupported" }
                        .joinToString(" / ") { it.path("text").stringValue("") }
                        .ifBlank { null }
                    continue
                }

                val applied = apply(parsed)
                val gen = saveGeneration(
                    teamId, kind, groundingJson, prompt, output,
                    if (applied) "accepted" else "failed", attempt, genMs, now,
                )
                saveEval(gen.id, judge, faithful = true, judgeMs, now)
                if (applied) return true
            } catch (e: Exception) {
                log.warn("phrase apply failed (team={}, kind={}, attempt={}): {}", teamId, kind, attempt, e.message)
                runCatching { saveGeneration(teamId, kind, groundingJson, prompt, output, "rejected", attempt, genMs, now) }
            }
        }
        return false
    }

    private fun saveGeneration(
        teamId: UUID, kind: String, grounding: String, prompt: String, output: String?,
        status: String, attempt: Int, latencyMs: Long, now: Instant,
    ): LlmGenerationEntity = generations.save(
        LlmGenerationEntity(
            teamId = teamId, kind = kind, grounding = grounding, prompt = prompt, output = output,
            model = genModel, status = status, attempt = attempt, latencyMs = latencyMs, createdAt = now,
        ),
    )

    private fun saveEval(generationId: UUID, judge: JsonNode, faithful: Boolean, latencyMs: Long, now: Instant) {
        evals.save(
            EvalResultEntity(
                generationId = generationId, judgeModel = judgeModel,
                // MissingNode.toString() is "" — invalid for a NOT NULL jsonb column.
                claims = judge.path("claims").takeIf { it.isArray }?.toString() ?: "[]",
                faithful = faithful,
                latencyMs = latencyMs, createdAt = now,
            ),
        )
    }

    private fun msSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
