package com.goldpet.domain.admin.audit

object AuditTargetExtractor {

    private val targetMappings: List<Pair<Regex, String>> = listOf(
        Regex("^/api/v1/admin/system/admins/(\\d+)(/.*)?$") to "ADMIN_USER",
        Regex("^/api/v1/admin/lbs/places/(\\d+)(/.*)?$") to "PLACE",
        Regex("^/api/v1/admin/gamification/badges/(\\d+)(/.*)?$") to "BADGE",
        Regex("^/api/v1/admin/users/(\\d+)(/.*)?$") to "USER",
        Regex("^/api/v1/admin/community/posts/(\\d+)(/.*)?$") to "POST",
        Regex("^/api/v1/admin/community/comments/(\\d+)(/.*)?$") to "COMMENT",
        Regex("^/api/v1/admin/reports/(\\d+)(/.*)?$") to "REPORT",
        Regex("^/api/v1/admin/chats/(\\d+)(/.*)?$") to "CHAT",
    )

    fun extract(path: String): AuditTarget? {
        for ((regex, type) in targetMappings) {
            val match = regex.find(path)
            if (match != null) {
                return AuditTarget(type, match.groupValues[1].toLongOrNull())
            }
        }
        val segments = path.removePrefix("/api/v1/admin/").split("/")
        val targetType = segments.firstOrNull()?.uppercase()?.removeSuffix("S") ?: return null
        val targetId = segments.getOrNull(1)?.toLongOrNull()
        return AuditTarget(targetType, targetId)
    }

    fun extractPair(path: String): Pair<String?, Long?> {
        val target = extract(path) ?: return Pair(null, null)
        return Pair(target.type, target.id)
    }
}
