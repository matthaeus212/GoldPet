package com.goldpet.domain.emoticon.repository

import com.goldpet.domain.emoticon.entity.EmoticonPack
import org.springframework.data.jpa.repository.JpaRepository

interface EmoticonPackRepository : JpaRepository<EmoticonPack, Long> {
    fun findAllByIsActiveTrueOrderBySortOrder(): List<EmoticonPack>
    fun findAllByOrderBySortOrder(): List<EmoticonPack>
}
