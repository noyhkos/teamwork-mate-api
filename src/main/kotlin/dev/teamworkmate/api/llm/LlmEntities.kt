package dev.teamworkmate.api.llm

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/** One LLM call for one phrase kind — full audit: what we grounded, asked, got, and decided. */
@Entity
@Table(name = "llm_generations")
class LlmGenerationEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "team_id", nullable = false) val teamId: UUID,
    @Column(nullable = false) val kind: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) val grounding: String,
    @Column(nullable = false) val prompt: String,
    val output: String?,
    @Column(nullable = false) val model: String,
    @Column(nullable = false) val status: String, // accepted | rejected | failed
    @Column(nullable = false) val attempt: Int,
    @Column(name = "latency_ms") val latencyMs: Long?,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
)

interface LlmGenerationRepository : JpaRepository<LlmGenerationEntity, UUID> {
    fun findByTeamIdOrderByCreatedAt(teamId: UUID): List<LlmGenerationEntity>
}

/** Judge verdict for one generation — the faithfulness eval trail. */
@Entity
@Table(name = "eval_results")
class EvalResultEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "generation_id", nullable = false) val generationId: UUID,
    @Column(name = "judge_model", nullable = false) val judgeModel: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) val claims: String,
    @Column(nullable = false) val faithful: Boolean,
    @Column(name = "latency_ms") val latencyMs: Long?,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
)

interface EvalResultRepository : JpaRepository<EvalResultEntity, UUID> {
    fun findByGenerationIdIn(generationIds: Collection<UUID>): List<EvalResultEntity>
}
