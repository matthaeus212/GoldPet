package com.goldpet.domain.admin.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * [AdminAuditDetails] sealed interface 의 JSON round-trip 헬퍼.
 *
 * - [encode]: 강타입 details → JSON string. null → null.
 * - [decode]: 저장된 plain string → AdminAuditDetails. JSON 파싱 실패하면 [AdminAuditDetails.Unknown] 으로 fallback.
 *
 * Spring 컨텍스트와 분리된 object 라 단순 단위테스트에서도 사용 가능.
 */
object AdminAuditDetailsCodec {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    fun encode(details: AdminAuditDetails?): String? {
        if (details == null) return null
        return mapper.writeValueAsString(details)
    }

    /**
     * 저장된 audit row 의 details 컬럼을 강타입으로 복원한다.
     * - null/blank → null
     * - 잘 형성된 polymorphic JSON → 해당 sealed variant
     * - 그 외 (legacy plain string, 깨진 JSON) → [AdminAuditDetails.Unknown] (raw=원문)
     */
    fun decode(raw: String?): AdminAuditDetails? {
        if (raw.isNullOrBlank()) return null
        return try {
            mapper.readValue(raw, AdminAuditDetails::class.java)
        } catch (e: Exception) {
            AdminAuditDetails.Unknown(raw = raw)
        }
    }
}
