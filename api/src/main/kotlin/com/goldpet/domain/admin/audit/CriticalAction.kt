package com.goldpet.domain.admin.audit

/**
 * Admin critical action marker (PII access, role change, admin CRUD, password reset, backup 등).
 *
 * 메서드 진입 전후로 [CriticalActionAuditAspect] 가 wrap 하여 audit log 를 적재한다.
 * - audit 적재 실패 시 5xx 강제 (실패한 critical action 은 silent 로 남기지 않는다).
 * - 기존 [com.goldpet.config.security.AdminAuditInterceptor] 는 200 응답 일반 audit 그대로 유지.
 *
 * @param action audit_logs.action 컬럼에 적재될 식별자 (e.g., "ADMIN_CREATE", "PASSWORD_RESET", "ROLE_CHANGE").
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CriticalAction(val action: String)
