package com.goldpet.domain.common.service

import com.goldpet.domain.common.dto.AppVersionResponse
import org.springframework.stereotype.Service

@Service
class AppVersionService(
    private val systemSettingService: SystemSettingService
) {

    fun checkVersion(platform: String, currentVersion: String): AppVersionResponse {
        val minimumVersion = systemSettingService.getString("app.version.${platform}.minimum", "1.0.0")
        val latestVersion = systemSettingService.getString("app.version.${platform}.latest", "1.0.0")
        val updateUrl = systemSettingService.getString("app.version.update_url.${platform}", "")

        val forceUpdate = compareVersions(currentVersion, minimumVersion) < 0
        val softUpdate = !forceUpdate && compareVersions(currentVersion, latestVersion) < 0

        return AppVersionResponse(
            forceUpdate = forceUpdate,
            softUpdate = softUpdate,
            minimumVersion = minimumVersion,
            latestVersion = latestVersion,
            updateUrl = updateUrl
        )
    }

    /**
     * Compares two semantic version strings numerically.
     * Returns negative if v1 < v2, 0 if equal, positive if v1 > v2.
     * Handles cases like "1.9.0" < "1.10.0" correctly.
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
