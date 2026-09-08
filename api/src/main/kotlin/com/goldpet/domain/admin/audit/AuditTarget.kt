package com.goldpet.domain.admin.audit

/**
 * audit log 의 (target_type, target_id) 컬럼 쌍을 강타입으로 묶는다.
 *
 * 기존 `AdminAuditService.log(targetType, targetId, ...)` String 시그니처는 유지하고,
 * 신규 sealed-details 변형은 [AuditTarget] 를 받아 동일한 컬럼을 채운다.
 */
data class AuditTarget(
    val type: String,
    val id: Long? = null
)
