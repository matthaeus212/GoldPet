package com.goldpet.domain.admin.repository

import com.goldpet.domain.admin.entity.AdminUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

interface AdminUserRepository : JpaRepository<AdminUser, Long> {
    /**
     * 로그인 조회는 email_hash(BlindIndex HMAC-SHA256)로 수행한다.
     * email 컬럼은 random-IV AES-GCM으로 암호화되어 equality 검색이 불가능하다.
     * 호출부: `BlindIndexUtil.hash(email)` 결과를 넘긴다.
     */
    fun findByEmailHash(emailHash: String): Optional<AdminUser>

    /**
     * EXT-CDX-005 마이그레이션 — otp_secret 컬럼의 원시값(converter 우회)을 읽는다.
     * 평문/암호문 판별 후 평문만 암호화 재저장하기 위해 native 로 raw 값을 조회.
     * 반환: [id, otp_secret] Array 목록.
     */
    @Query(value = "SELECT id, otp_secret FROM admin_users WHERE otp_secret IS NOT NULL AND otp_secret <> ''", nativeQuery = true)
    fun findRawOtpSecrets(): List<Array<Any>>

    /** EXT-CDX-005 마이그레이션 — 암호화된 otp_secret 을 native 로 원자적 갱신(converter 우회). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE admin_users SET otp_secret = :enc WHERE id = :id", nativeQuery = true)
    fun updateRawOtpSecret(@Param("id") id: Long, @Param("enc") enc: String)
}
