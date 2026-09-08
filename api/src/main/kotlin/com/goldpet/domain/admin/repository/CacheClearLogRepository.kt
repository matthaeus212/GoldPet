package com.goldpet.domain.admin.repository

import com.goldpet.domain.admin.entity.CacheClearLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CacheClearLogRepository : JpaRepository<CacheClearLog, Long> {
    fun findAllByOrderByClearedAtDesc(pageable: Pageable): Page<CacheClearLog>
    fun findFirstByCacheNameOrderByClearedAtDesc(cacheName: String): CacheClearLog?
    fun findFirstByOrderByClearedAtDesc(): CacheClearLog?
}
