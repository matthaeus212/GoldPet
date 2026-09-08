package com.goldpet.domain.emoticon.repository

import com.goldpet.domain.emoticon.entity.Emoticon
import org.springframework.data.jpa.repository.JpaRepository

interface EmoticonRepository : JpaRepository<Emoticon, Long> {
    fun findAllByIsActiveTrueOrderBySortOrder(): List<Emoticon>
    fun findAllByIdIn(ids: List<Long>): List<Emoticon>
    fun findAllByPackIdAndIsActiveTrueOrderBySortOrder(packId: Long): List<Emoticon>
    fun findAllByOrderBySortOrder(): List<Emoticon>
}
