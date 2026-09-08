package com.goldpet.domain.admin.repository

import com.goldpet.domain.admin.entity.Campaign
import org.springframework.data.jpa.repository.JpaRepository

interface CampaignRepository : JpaRepository<Campaign, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<Campaign>
}
