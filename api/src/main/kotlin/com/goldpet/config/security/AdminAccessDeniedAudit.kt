package com.goldpet.config.security

import com.goldpet.common.net.TrustedClientIpResolver
import com.goldpet.domain.admin.service.AdminAuditService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder

object AdminAccessDeniedAudit {
    private val log = LoggerFactory.getLogger(AdminAccessDeniedAudit::class.java)

    fun record(request: HttpServletRequest, adminAuditService: AdminAuditService?) {
        val path = request.requestURI
        if (!path.startsWith("/api/v1/admin/")) return
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal !is AdminUserPrincipal) return
        if (adminAuditService == null) return
        runCatching {
            adminAuditService.log(
                adminUserId = principal.id,
                action = "ACCESS_DENIED",
                ipAddress = TrustedClientIpResolver.resolveOrNull(request),
                requestPath = path,
                requestMethod = request.method,
                responseStatus = HttpStatus.FORBIDDEN.value()
            )
        }.onFailure { log.warn("Failed to record ACCESS_DENIED audit for path={}", path, it) }
    }
}
