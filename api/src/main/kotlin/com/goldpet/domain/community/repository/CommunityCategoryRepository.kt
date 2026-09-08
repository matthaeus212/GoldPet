package com.goldpet.domain.community.repository

import com.goldpet.domain.community.entity.CommunityCategory
import org.springframework.data.jpa.repository.JpaRepository

interface CommunityCategoryRepository : JpaRepository<CommunityCategory, Long> {
    fun findByCode(code: String): CommunityCategory?
}
