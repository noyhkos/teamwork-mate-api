package dev.teamworkmate.api.team

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "members")
class Member(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "team_id", nullable = false)
    val teamId: UUID,

    @Column(nullable = false)
    val nickname: String,

    @Column(name = "birth_date", nullable = false)
    val birthDate: LocalDate,

    // null means birth time unknown — calc must not fabricate an hour pillar
    @Column(name = "birth_time")
    val birthTime: LocalTime? = null,

    @Column(nullable = false)
    val gender: String, // M | F

    @Column(nullable = false)
    val mbti: String,

    @Column(nullable = false)
    val calendar: String = "solar",

    @Column(name = "leap_month", nullable = false)
    val leapMonth: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
