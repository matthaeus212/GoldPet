package com.goldpet.domain.user.repository

import com.goldpet.domain.user.entity.UserDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface UserDeviceRepository : JpaRepository<UserDevice, Long> {
    fun findByUserIdAndDeviceId(userId: Long, deviceId: String): Optional<UserDevice>
    fun findAllByUserIdAndIsActiveTrue(userId: Long): List<UserDevice>
    fun findByFcmToken(fcmToken: String): Optional<UserDevice>
    fun countByUserIdAndIsActiveTrue(userId: Long): Int

    @Query("SELECT d FROM UserDevice d WHERE d.user.id = :userId AND d.isActive = true ORDER BY d.lastLoginAt ASC")
    fun findOldestActiveByUserId(@Param("userId") userId: Long): List<UserDevice>

    @Modifying
    @Query("UPDATE UserDevice d SET d.isActive = false WHERE d.user.id = :userId")
    fun deactivateAllByUserId(@Param("userId") userId: Long): Int

    @Modifying
    @Query("UPDATE UserDevice d SET d.isActive = false WHERE d.fcmToken = :fcmToken AND d.user.id != :userId AND d.isActive = true")
    fun deactivateByFcmTokenExcludingUser(@Param("fcmToken") fcmToken: String?, @Param("userId") userId: Long): Int

    /** Sprint 4 BLOCKER #8 — FCM UNREGISTERED/INVALID_ARGUMENT 신호 받은 무효 토큰 cleanup. */
    @Modifying
    @Query("UPDATE UserDevice d SET d.isActive = false WHERE d.fcmToken = :fcmToken AND d.isActive = true")
    fun deactivateByFcmToken(@Param("fcmToken") fcmToken: String): Int

    @Modifying
    @Query("DELETE FROM UserDevice d WHERE d.isActive = false AND d.updatedAt < :cutoffDate")
    fun deleteStaleDevices(@Param("cutoffDate") cutoffDate: LocalDateTime): Int

    fun deleteAllByUserId(userId: Long)
}
