package com.goldpet.domain.admin.audit

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.goldpet.domain.admin.entity.AdminUserRole

/**
 * 관리자 audit log 의 details 컬럼에 직렬화되는 강타입 표현.
 *
 * - V60 이전 row 의 details 컬럼은 plain string 으로 적재돼 있어서, 신규 코드에서 역직렬화 시
 *   JSON parse 실패 → [Unknown] 으로 fallback 한다.
 * - 신규 sealed type 추가 시 [JsonSubTypes] 에 등록 + round-trip 테스트 추가.
 *
 * 직렬화/역직렬화는 [AdminAuditDetailsCodec] 를 사용한다.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "_type",
    defaultImpl = AdminAuditDetails.Unknown::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AdminAuditDetails.RoleChange::class, name = "RoleChange"),
    JsonSubTypes.Type(value = AdminAuditDetails.AdminCreate::class, name = "AdminCreate"),
    JsonSubTypes.Type(value = AdminAuditDetails.AdminDelete::class, name = "AdminDelete"),
    JsonSubTypes.Type(value = AdminAuditDetails.Failure::class, name = "Failure"),
    JsonSubTypes.Type(value = AdminAuditDetails.Backup::class, name = "Backup"),
    JsonSubTypes.Type(value = AdminAuditDetails.PasswordReset::class, name = "PasswordReset"),
    JsonSubTypes.Type(value = AdminAuditDetails.PlaceUpdate::class, name = "PlaceUpdate"),
    JsonSubTypes.Type(value = AdminAuditDetails.Unknown::class, name = "Unknown"),
)
sealed interface AdminAuditDetails {
    data class RoleChange(
        val before: AdminUserRole,
        val after: AdminUserRole
    ) : AdminAuditDetails

    data class AdminCreate(
        val role: AdminUserRole,
        val email: String
    ) : AdminAuditDetails

    data class AdminDelete(
        val role: AdminUserRole,
        val isActive: Boolean
    ) : AdminAuditDetails

    data class Failure(
        val reason: String
    ) : AdminAuditDetails

    data class Backup(
        val filePath: String?,
        val sizeBytes: Long?
    ) : AdminAuditDetails

    data class PasswordReset(
        val target: String
    ) : AdminAuditDetails

    data class PlaceUpdate(
        val beforeLat: Double?,
        val afterLat: Double?,
        val beforeLon: Double?,
        val afterLon: Double?
    ) : AdminAuditDetails

    /**
     * Fallback. JSON 으로 표현되지 않는 legacy plain-string row 또는 미등록 _type 을 흡수한다.
     */
    data class Unknown(
        val raw: String? = null
    ) : AdminAuditDetails
}
