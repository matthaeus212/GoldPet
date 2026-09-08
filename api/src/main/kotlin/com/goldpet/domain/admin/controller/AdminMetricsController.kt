package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.AdminMetricsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 포스트런치 메트릭 — derive-first JSON 엔드포인트(plan §W2, 대시보드는 W3 보류).
 *
 * 모든 엔드포인트는 native-SQL-GROUP-BY 집계만 노출하며(MDE/유의성은 범위 밖, raw count+rate),
 * 각 응답에 캡처 테이블의 최초 행 날짜(`dataAvailableSince`)와 윈도우가 캡처 이전으로 새는지
 * (`windowClampedToCapture`)를 함께 돌려준다 — half-populated D7 을 진짜처럼 반환하지 않기 위함.
 */
@Tag(name = "Admin Metrics", description = "관리자 포스트런치 메트릭 API (A/B match-rate, 미션, 재참여, 부스트, 리텐션, 스트릭)")
@RestController
@RequestMapping("/api/v1/admin/metrics")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
@Validated
class AdminMetricsController(
    private val adminMetricsService: AdminMetricsService,
) {
    @Operation(summary = "궁합 매칭 A/B match-rate (cohort별 전환율, NONE 분모 제외 + noneFraction)")
    @GetMapping("/match-rate")
    fun getMatchRate(
        @RequestParam(defaultValue = "30") @Min(1) @Max(365) days: Int,
    ): ResponseEntity<MatchRateResponse> =
        ResponseEntity.ok(adminMetricsService.getMatchRate(days))

    @Operation(summary = "데일리 미션 완료율 (user_badges DAILY vs daily_mission_set / 일)")
    @GetMapping("/mission-completion")
    fun getMissionCompletion(
        @RequestParam(defaultValue = "30") @Min(1) @Max(365) days: Int,
    ): ResponseEntity<MissionCompletionResponse> =
        ResponseEntity.ok(adminMetricsService.getMissionCompletion(days))

    @Operation(summary = "재참여 넛지 복귀율 + opt-out 가드레일")
    @GetMapping("/re-engagement")
    fun getReengagement(
        @RequestParam(defaultValue = "30") @Min(1) @Max(365) days: Int,
        @RequestParam(defaultValue = "7") @Min(1) @Max(90) returnWindowDays: Int,
    ): ResponseEntity<ReengagementResponse> =
        ResponseEntity.ok(adminMetricsService.getReengagement(days, returnWindowDays))

    @Operation(summary = "프로필 부스트 구매 수 + 소비 골드")
    @GetMapping("/boost")
    fun getBoost(
        @RequestParam(defaultValue = "30") @Min(1) @Max(365) days: Int,
    ): ResponseEntity<BoostResponse> =
        ResponseEntity.ok(adminMetricsService.getBoost(days))

    @Operation(summary = "DAU + signup-cohort D1/D7 리텐션 (성숙도 플래그)")
    @GetMapping("/retention")
    fun getRetention(
        @RequestParam(defaultValue = "30") @Min(1) @Max(365) days: Int,
    ): ResponseEntity<RetentionResponse> =
        ResponseEntity.ok(adminMetricsService.getRetention(days))

    @Operation(summary = "스트릭-리텐션 상관 (CORRELATIONAL, 비인과 — note 필드 명시)")
    @GetMapping("/streak-correlation")
    fun getStreakCorrelation(
        @RequestParam(defaultValue = "60") @Min(1) @Max(365) days: Int,
    ): ResponseEntity<StreakCorrelationResponse> =
        ResponseEntity.ok(adminMetricsService.getStreakCorrelation(days))
}

// ----------------------------------------------------------------------
// Response DTOs
// ----------------------------------------------------------------------

/** 윈도우 메타: 요청 윈도우, 실제 적용 윈도우(캡처 시작으로 클램프), 캡처 가용 시작일. */
data class WindowMeta(
    val requestedFrom: LocalDate,
    val effectiveFrom: LocalDate,
    val to: LocalDate,
    val dataAvailableSince: LocalDate?,
    val windowClampedToCapture: Boolean,
)

data class CohortConversion(
    val cohort: String,
    val likeActions: Long,
    val matches: Long,
    /** matches / likeActions. likeActions=0 이면 null. */
    val matchRate: Double?,
)

data class MatchRateResponse(
    val window: WindowMeta,
    val treatment: CohortConversion,
    val control: CohortConversion,
    /** 미노출(NONE) like 비중 = noneLikeActions / 전체 likeActions. 높을수록 A/B 표본 대표성 ↓ (validity 게이지). */
    val noneFraction: Double,
)

data class MissionCompletionDay(
    val date: LocalDate,
    val missionsOffered: Long,
    val completions: Long,
    val uniqueCompleters: Long,
)

data class MissionCompletionResponse(
    val window: WindowMeta,
    val days: List<MissionCompletionDay>,
)

data class ReengagementByType(
    val nudgeType: String,
    val sends: Long,
    val returned: Long,
    /** returned / sends. sends=0 이면 null. */
    val returnRate: Double?,
)

data class ReengagementResponse(
    val window: WindowMeta,
    val returnWindowDays: Int,
    val byType: List<ReengagementByType>,
    val totalSends: Long,
    val totalReturned: Long,
    val overallReturnRate: Double?,
    /** opt-out 가드레일: 재참여 알림 비활성 유저 비율. */
    val optOutFraction: Double,
    val optedOutUsers: Long,
    val totalUsers: Long,
    val note: String,
)

data class BoostResponse(
    val window: WindowMeta,
    val boostCount: Long,
    val goldSpent: Long,
    val uniqueUsers: Long,
)

data class DauDay(
    val date: LocalDate,
    val dau: Long,
)

data class RetentionCohort(
    val cohortDate: LocalDate,
    val cohortSize: Long,
    val d1Retained: Long,
    val d7Retained: Long,
    val d1Rate: Double?,
    val d7Rate: Double?,
    /** day+1 active 윈도우가 캡처 범위 내 + 오늘까지 경과 → D1 신뢰 가능. */
    val d1Mature: Boolean,
    /** day+7 active 윈도우가 캡처 범위 내 + 오늘까지 경과 → D7 신뢰 가능. */
    val d7Mature: Boolean,
)

data class RetentionResponse(
    val window: WindowMeta,
    val dau: List<DauDay>,
    val cohorts: List<RetentionCohort>,
)

data class StreakCorrelationGroup(
    val hasStreak: Boolean,
    val users: Long,
    val d7Active: Long,
    val d7Rate: Double?,
)

data class StreakCorrelationResponse(
    val window: WindowMeta,
    val withStreak: StreakCorrelationGroup,
    val withoutStreak: StreakCorrelationGroup,
    /** 인과 주장 금지 라벨. */
    val note: String,
)
