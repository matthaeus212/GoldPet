package com.goldpet.domain.profileboost.repository

import com.goldpet.domain.profileboost.entity.ProfileBoost
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface ProfileBoostRepository : JpaRepository<ProfileBoost, Long> {
    /** 현재 활성(미만료) 부스트 중 가장 늦게 만료되는 1건. */
    fun findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(
        userId: Long,
        now: LocalDateTime
    ): ProfileBoost?

    /** 친구 랭킹 훅: 현재 부스트 활성 유저 id 집합 조회용. */
    fun findAllByExpiresAtAfter(now: LocalDateTime): List<ProfileBoost>
}
