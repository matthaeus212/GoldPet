package com.goldpet.config.security

import com.goldpet.common.net.TrustedClientIpResolver
import com.goldpet.domain.admin.audit.AuditTargetExtractor
import com.goldpet.domain.admin.audit.CriticalAction
import com.goldpet.domain.admin.service.AdminAuditService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AdminAuditInterceptor(
    private val adminAuditService: AdminAuditService,
    private val trustedClientIpResolver: TrustedClientIpResolver
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val path = request.requestURI
        if (!path.startsWith("/api/v1/admin/")) return
        if (path.contains("/audit-logs")) return
        if (handler is HandlerMethod && handler.hasMethodAnnotation(CriticalAction::class.java)) return

        val adminUserId = extractAdminUserId(request) ?: return

        try {
            val action = determineAction(request.method, path)
            val (targetType, targetId) = extractTarget(path)

            adminAuditService.log(
                adminUserId = adminUserId,
                action = action,
                targetType = targetType,
                targetId = targetId,
                ipAddress = trustedClientIpResolver.resolveOrNull(request),
                requestPath = path,
                requestMethod = request.method,
                responseStatus = response.status,
                userAgent = request.getHeader("User-Agent")
            )
        } catch (e: Exception) {
            log.warn("Audit logging failed: ${e.message}")
        }
    }

    private fun extractAdminUserId(request: HttpServletRequest): Long? {
        val adminId = request.getAttribute("adminUserId") as? Long
        if (adminId != null) return adminId

        val auth = SecurityContextHolder.getContext().authentication
        val principal = auth?.principal
        if (principal is AdminUserPrincipal) return principal.id
        return try {
            auth?.name?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun determineAction(method: String, path: String): String {
        return when (method.uppercase()) {
            "GET" -> "VIEW"
            "POST" -> if (path.contains("export")) "EXPORT" else "CREATE"
            "PUT", "PATCH" -> "UPDATE"
            "DELETE" -> "DELETE"
            else -> "OTHER"
        }
    }

    private fun extractTarget(path: String): Pair<String?, Long?> = AuditTargetExtractor.extractPair(path)
}
