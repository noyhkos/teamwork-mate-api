package dev.teamworkmate.api.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "role_scores")
class RoleScoreEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "team_id", nullable = false) val teamId: UUID,
    @Column(name = "member_id", nullable = false) val memberId: UUID,
    @Column(nullable = false) val role: String,
    @Column(nullable = false) val score: Double,
    @Column(nullable = false) val assigned: Boolean = false,
    var reason: String? = null, // judge-accepted LLM phrase; NULL serves the template
)

interface RoleScoreRepository : JpaRepository<RoleScoreEntity, UUID> {
    // Bulk JPQL delete executes immediately — derived deletes are queued after inserts
    // in Hibernate's action order and would break rerun idempotency.
    @Modifying
    @Query("delete from RoleScoreEntity r where r.teamId = :teamId")
    fun deleteByTeamId(teamId: UUID): Int

    fun findByTeamIdAndAssignedTrue(teamId: UUID): List<RoleScoreEntity>

    // Targeted update: a detached entity merge would resurrect rows a concurrent
    // rerun already deleted. 0 rows affected simply means "superseded".
    @Transactional
    @Modifying
    @Query("update RoleScoreEntity r set r.reason = :reason where r.id = :id")
    fun updateReason(@Param("id") id: UUID, @Param("reason") reason: String): Int
}

@Entity
@Table(name = "pair_scores")
class PairScoreEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "team_id", nullable = false) val teamId: UUID,
    @Column(name = "member_a_id", nullable = false) val memberAId: UUID,
    @Column(name = "member_b_id", nullable = false) val memberBId: UUID,
    @Column(nullable = false) val total: Int,
    @Column(name = "saju_score", nullable = false) val sajuScore: Int,
    @Column(name = "mbti_score", nullable = false) val mbtiScore: Int,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) val factors: String,
    var reason: String? = null, // judge-accepted LLM phrase; NULL serves the template
)

interface PairScoreRepository : JpaRepository<PairScoreEntity, UUID> {
    @Modifying
    @Query("delete from PairScoreEntity p where p.teamId = :teamId")
    fun deleteByTeamId(teamId: UUID): Int

    // id breaks ties deterministically so enrichment and the report agree on best/worst.
    fun findByTeamIdOrderByTotalDescIdAsc(teamId: UUID): List<PairScoreEntity>

    @Transactional
    @Modifying
    @Query("update PairScoreEntity p set p.reason = :reason where p.id = :id")
    fun updateReason(@Param("id") id: UUID, @Param("reason") reason: String): Int
}

@Entity
@Table(name = "team_analysis")
class TeamAnalysisEntity(
    @Id @Column(name = "team_id") val teamId: UUID,
    @Column(name = "archetype_name", nullable = false) val archetypeName: String,
    @Column(name = "archetype_desc") val archetypeDesc: String?,
    @Column(name = "harmony_score", nullable = false) val harmonyScore: Int,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "trait_avgs", nullable = false) val traitAvgs: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "element_totals", nullable = false) val elementTotals: String,
    @Column(name = "risk_note") val riskNote: String?,
    @Column(name = "analyzed_at", nullable = false) val analyzedAt: Instant,
    var intro: String? = null, // judge-accepted LLM team intro
)

interface TeamAnalysisRepository : JpaRepository<TeamAnalysisEntity, UUID> {
    // analyzedAt acts as a run token: an enrich grounded on an older analysis
    // updates 0 rows instead of overwriting a newer one.
    @Transactional
    @Modifying
    @Query("update TeamAnalysisEntity t set t.intro = :intro where t.teamId = :teamId and t.analyzedAt = :analyzedAt")
    fun updateIntro(
        @Param("teamId") teamId: UUID,
        @Param("intro") intro: String,
        @Param("analyzedAt") analyzedAt: Instant,
    ): Int
}
