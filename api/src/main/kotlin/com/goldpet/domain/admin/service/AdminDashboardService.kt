package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.ChartDataPoint
import com.goldpet.domain.admin.controller.DashboardChartsResponse
import com.goldpet.domain.admin.controller.DashboardStatsResponse
import com.goldpet.domain.admin.controller.RecentActivityItem
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.repository.GoldTransactionRepository
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.report.entity.ReportStatus
import com.goldpet.domain.report.repository.ReportRepository
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.repository.WalkRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class AdminDashboardService(
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val postRepository: CommunityPostRepository,
    private val commentRepository: CommunityCommentRepository,
    private val walkRepository: WalkRepository,
    private val goldTransactionRepository: GoldTransactionRepository,
    private val reportRepository: ReportRepository
) {

    fun getStats(): DashboardStatsResponse {
        val today = LocalDate.now()
        val startOfToday = today.atStartOfDay()
        val startOfWeek = today.minusDays(7).atStartOfDay()

        val totalUsers = userRepository.count()
        val newUsersToday = userRepository.countByCreatedAtAfter(startOfToday)
        val newUsersThisWeek = userRepository.countByCreatedAtAfter(startOfWeek)
        val totalPets = petRepository.count()

        val totalPosts = postRepository.count()
        val totalComments = commentRepository.count()

        return DashboardStatsResponse(
            totalUsers = totalUsers,
            newUsersToday = newUsersToday,
            newUsersThisWeek = newUsersThisWeek,
            totalPets = totalPets,
            totalWalks = walkRepository.count(),
            totalDistance = walkRepository.sumTotalDistanceKm(),
            totalPosts = totalPosts,
            totalComments = totalComments,
            totalReports = reportRepository.count().toInt(),
            pendingReports = reportRepository.countByStatus(ReportStatus.PENDING).toInt(),
            revenue = goldTransactionRepository.sumAmountByType(TransactionType.CHARGE),
            goldCirculation = userRepository.sumGoldBalance()
        )
    }

    fun getCharts(days: Int): DashboardChartsResponse {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = LocalDate.now()
        val since = today.minusDays(days.toLong()).atStartOfDay()

        // Fetch grouped data from DB
        val userGrowthData = userRepository.countByDateGrouped(since).associate {
            it[0].toString() to (it[1] as Number).toLong()
        }
        val walkActivityData = walkRepository.countByDateGrouped(since).associate {
            it[0].toString() to (it[1] as Number).toLong()
        }
        val revenueData = goldTransactionRepository.sumByDateGrouped(since).associate {
            it[0].toString() to (it[1] as Number).toLong()
        }

        // Fill missing dates with 0
        val userGrowth = (0 until days).map { i ->
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(formatter)
            ChartDataPoint(date = dateStr, value = userGrowthData[dateStr] ?: 0L)
        }.reversed()

        val walkActivity = (0 until days).map { i ->
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(formatter)
            ChartDataPoint(date = dateStr, value = walkActivityData[dateStr] ?: 0L)
        }.reversed()

        val revenue = (0 until days).map { i ->
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(formatter)
            ChartDataPoint(date = dateStr, value = revenueData[dateStr] ?: 0L)
        }.reversed()

        return DashboardChartsResponse(
            userGrowth = userGrowth,
            walkActivity = walkActivity,
            revenue = revenue
        )
    }

    fun getRecentActivity(limit: Int): List<RecentActivityItem> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val activities = mutableListOf<RecentActivityItem>()

        userRepository.findAll().take(limit / 3).forEach { user ->
            activities.add(RecentActivityItem(
                id = user.id,
                type = "USER_JOIN",
                description = "${user.nickname ?: "사용자"}님이 가입했습니다.",
                createdAt = user.createdAt.format(formatter)
            ))
        }

        postRepository.findAll().take(limit / 3).forEach { post ->
            activities.add(RecentActivityItem(
                id = post.id,
                type = "POST_CREATE",
                description = "\"${post.title}\" 게시글이 작성되었습니다.",
                createdAt = post.createdAt.format(formatter)
            ))
        }

        return activities.sortedByDescending { it.createdAt }.take(limit)
    }
}
