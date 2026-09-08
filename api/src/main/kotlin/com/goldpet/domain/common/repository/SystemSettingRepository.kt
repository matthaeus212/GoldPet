package com.goldpet.domain.common.repository

import com.goldpet.domain.common.entity.SystemSetting
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SystemSettingRepository : JpaRepository<SystemSetting, String> {
    fun findByKey(key: String): SystemSetting?
}
