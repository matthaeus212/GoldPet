package com.goldpet.domain.common.repository

import com.goldpet.domain.common.entity.AppNotice
import com.goldpet.domain.common.entity.AppNoticeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface AppNoticeRepository : JpaRepository<AppNotice, Long> {

    @Query("""
        SELECT n FROM AppNotice n
        WHERE n.isActive = true
          AND n.startAt <= :now
          AND (n.endAt IS NULL OR n.endAt > :now)
          AND (n.targetScreen = :screen OR n.targetScreen = 'ALL')
        ORDER BY n.priority DESC
    """)
    fun findActiveNotices(
        @Param("now") now: LocalDateTime,
        @Param("screen") screen: String
    ): List<AppNotice>

    @Query("""
        SELECT n FROM AppNotice n
        WHERE n.isActive = true
          AND n.type = :type
          AND n.startAt <= :now
          AND (n.endAt IS NULL OR n.endAt > :now)
        ORDER BY n.priority DESC
    """)
    fun findActiveByType(
        @Param("type") type: AppNoticeType,
        @Param("now") now: LocalDateTime
    ): List<AppNotice>
}
