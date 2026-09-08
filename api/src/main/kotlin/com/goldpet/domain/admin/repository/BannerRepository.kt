package com.goldpet.domain.admin.repository

import com.goldpet.domain.admin.entity.Banner
import com.goldpet.domain.admin.entity.BannerPlacement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface BannerRepository : JpaRepository<Banner, Long> {
    fun findAllByOrderByDisplayOrderAsc(): List<Banner>
    fun findAllByIsActiveTrueOrderByDisplayOrderAsc(): List<Banner>
    fun findAllByPlacementOrderByDisplayOrderAsc(placement: BannerPlacement): List<Banner>

    /**
     * 공개 배너 조회 — active + 기간윈도우(start/end null 허용) 전체를 placement/displayOrder 순으로.
     * idx_banners_placement_active(placement, is_active, display_order) 활용.
     */
    @Query(
        """
        SELECT b FROM Banner b
        WHERE b.isActive = true
          AND (b.startDate IS NULL OR b.startDate <= :now)
          AND (b.endDate IS NULL OR b.endDate >= :now)
        ORDER BY b.placement ASC, b.displayOrder ASC
        """
    )
    fun findActiveForPublic(@Param("now") now: LocalDateTime): List<Banner>
}
