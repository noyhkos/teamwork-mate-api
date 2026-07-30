package dev.teamworkmate.api.facts

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/** Deterministic saju facts from the calc service — the single grounding source for scores and LLM text. */
@Entity
@Table(name = "saju_facts")
class SajuFacts(
    @Id
    @Column(name = "member_id")
    val memberId: UUID,

    @Column(name = "birth_time_known", nullable = false)
    val birthTimeKnown: Boolean,

    @Column(name = "day_master", nullable = false)
    val dayMaster: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    val pillars: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "five_elements", nullable = false)
    val fiveElements: String,

    @Column(name = "wood_cnt", nullable = false) val woodCnt: Int,
    @Column(name = "fire_cnt", nullable = false) val fireCnt: Int,
    @Column(name = "earth_cnt", nullable = false) val earthCnt: Int,
    @Column(name = "metal_cnt", nullable = false) val metalCnt: Int,
    @Column(name = "water_cnt", nullable = false) val waterCnt: Int,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ten_gods", nullable = false)
    val tenGods: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "day_strength")
    val dayStrength: String?,

    val geukguk: String?,

    @JdbcTypeCode(SqlTypes.JSON)
    val samjae: String?,

    @Column(name = "compact_text", nullable = false)
    val compactText: String,

    @Column(name = "full_compact_text")
    val fullCompactText: String?,

    @Column(name = "calc_version", nullable = false)
    val calcVersion: String,

    @Column(name = "computed_at", nullable = false)
    val computedAt: Instant,
)

interface SajuFactsRepository : JpaRepository<SajuFacts, UUID>
