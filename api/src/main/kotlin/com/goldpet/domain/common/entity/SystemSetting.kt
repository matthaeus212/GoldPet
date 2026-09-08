package com.goldpet.domain.common.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "system_settings")
@EntityListeners(AuditingEntityListener::class)
class SystemSetting(
    @Id
    @Column(name = "setting_key", nullable = false, unique = true)
    val key: String,

    @Column(name = "setting_value", nullable = false)
    var value: String,

    @Column(name = "description")
    var description: String? = null
) {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
}
