package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.audit.AdminAuditDetails
import com.goldpet.domain.admin.audit.AdminAuditDetailsCodec
import com.goldpet.domain.admin.audit.AuditTarget
import com.goldpet.domain.admin.entity.AdminAuditLog
import com.goldpet.domain.admin.repository.AdminAuditLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminAuditService(
    private val auditLogRepository: AdminAuditLogRepository
) {
    @Transactional
    fun log(
        adminUserId: Long,
        action: String,
        targetType: String? = null,
        targetId: Long? = null,
        ipAddress: String? = null,
        requestPath: String? = null,
        requestMethod: String? = null,
        responseStatus: Int? = null,
        details: String? = null,
        userAgent: String? = null
    ) {
        auditLogRepository.save(
            AdminAuditLog(
                adminUserId = adminUserId,
                action = action,
                targetType = targetType,
                targetId = targetId,
                ipAddress = ipAddress,
                requestPath = requestPath,
                requestMethod = requestMethod,
                responseStatus = responseStatus,
                details = details,
                userAgent = userAgent
            )
        )
    }

    /**
     * 강타입 [AdminAuditDetails] / [AuditTarget] 변형. 기존 String 시그니처를 그대로 호출한다.
     * details 는 [AdminAuditDetailsCodec] 로 JSON 직렬화되어 동일한 details 컬럼에 적재된다.
     */
    @Transactional
    fun log(
        adminUserId: Long,
        action: String,
        target: AuditTarget? = null,
        details: AdminAuditDetails? = null,
        ipAddress: String? = null,
        requestPath: String? = null,
        requestMethod: String? = null,
        responseStatus: Int? = null,
        userAgent: String? = null
    ) {
        log(
            adminUserId = adminUserId,
            action = action,
            targetType = target?.type,
            targetId = target?.id,
            ipAddress = ipAddress,
            requestPath = requestPath,
            requestMethod = requestMethod,
            responseStatus = responseStatus,
            details = AdminAuditDetailsCodec.encode(details),
            userAgent = userAgent
        )
    }

    fun getAuditLogs(pageable: Pageable): Page<AdminAuditLog> {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
    }

    fun getAuditLogsByAdmin(adminUserId: Long, pageable: Pageable): Page<AdminAuditLog> {
        return auditLogRepository.findByAdminUserIdOrderByCreatedAtDesc(adminUserId, pageable)
    }
}
