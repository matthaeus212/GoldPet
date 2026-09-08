package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.AdminDashboardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

data class DashboardStatsResponse(
    val totalUsers: Long,
    val newUsersToday: Long,
    val newUsersThisWeek: Long,
    val totalPets: Long,
    val totalWalks: Long,
    val totalDistance: Double,
    val totalPosts: Long,
    val totalComments: Long,
    val totalReports: Int,
    val pendingReports: Int,
    val revenue: Long,
    val goldCirculation: Long
)

data class ChartDataPoint(
    val date: String,
    val value: Long
)

data class DashboardChartsResponse(
    val userGrowth: List<ChartDataPoint>,
    val walkActivity: List<ChartDataPoint>,
    val revenue: List<ChartDataPoint>
)

data class RecentActivityItem(
    val id: Long,
    val type: String,
    val description: String,
    val createdAt: String
)

@Tag(name = "Admin Dashboard", description = "관리자 대시보드 API")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminDashboardController(
    private val dashboardService: AdminDashboardService
) {

    @Operation(summary = "대시보드 통계")
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<DashboardStatsResponse> {
        return ResponseEntity.ok(dashboardService.getStats())
    }

    @Operation(summary = "대시보드 차트 데이터")
    @GetMapping("/charts")
    fun getCharts(
        @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<DashboardChartsResponse> {
        return ResponseEntity.ok(dashboardService.getCharts(days))
    }

    @Operation(summary = "최근 활동")
    @GetMapping("/recent-activity")
    fun getRecentActivity(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<RecentActivityItem>> {
        return ResponseEntity.ok(dashboardService.getRecentActivity(limit))
    }
}
