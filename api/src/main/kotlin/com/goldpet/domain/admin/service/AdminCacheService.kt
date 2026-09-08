package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.dto.CacheClearLogResponse
import com.goldpet.domain.admin.dto.CacheClearResult
import com.goldpet.domain.admin.dto.CacheInfoResponse
import com.goldpet.domain.admin.entity.CacheClearLog
import com.goldpet.domain.admin.repository.CacheClearLogRepository
import org.springframework.cache.CacheManager
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AdminCacheService(
    private val cacheManager: CacheManager,
    private val cacheClearLogRepository: CacheClearLogRepository
) {
    private data class CacheInfo(val name: String, val ttlMinutes: Long, val description: String)

    private val cacheRegistry = listOf(
        CacheInfo("breeds", 10, "견종 데이터"),
        CacheInfo("pet_attributes", 10, "펫 속성 데이터"),
        CacheInfo("pet:species", 1440, "펫 종류 데이터"),
        CacheInfo("pet:breeds", 1440, "펫 견종 캐시"),
        CacheInfo("community:categories", 1440, "커뮤니티 카테고리"),
        CacheInfo("community:categories:entities", 10, "커뮤니티 카테고리 엔티티"),
        CacheInfo("community:posts", 1, "커뮤니티 게시글 피드"),
        CacheInfo("system:settings", 1440, "시스템 설정")
    )

    fun getCacheStatus(): List<CacheInfoResponse> {
        return cacheRegistry.map { info ->
            val lastClear = cacheClearLogRepository.findFirstByCacheNameOrderByClearedAtDesc(info.name)
                ?: cacheClearLogRepository.findFirstByCacheNameOrderByClearedAtDesc("ALL")

            CacheInfoResponse(
                name = info.name,
                ttlMinutes = info.ttlMinutes,
                description = info.description,
                lastCleared = lastClear?.clearedAt
            )
        }
    }

    @Transactional
    fun clearCache(cacheName: String): CacheClearResult {
        return try {
            val cache = cacheManager.getCache(cacheName)
            if (cache != null) {
                cache.clear()
                cacheClearLogRepository.save(CacheClearLog(
                    cacheName = cacheName,
                    clearedBy = "admin",
                    reason = null
                ))
                CacheClearResult(cacheName = cacheName, success = true, message = "캐시가 초기화되었습니다")
            } else {
                CacheClearResult(cacheName = cacheName, success = false, message = "캐시를 찾을 수 없습니다: $cacheName")
            }
        } catch (e: Exception) {
            CacheClearResult(cacheName = cacheName, success = false, message = "캐시 초기화 실패: ${e.message}")
        }
    }

    @Transactional
    fun clearAllCaches(): CacheClearResult {
        return try {
            val cacheNames = cacheManager.cacheNames
            cacheNames.forEach { name ->
                cacheManager.getCache(name)?.clear()
            }
            cacheClearLogRepository.save(CacheClearLog(
                cacheName = "ALL",
                clearedBy = "admin",
                reason = "전체 캐시 초기화"
            ))
            CacheClearResult(cacheName = "ALL", success = true, message = "${cacheNames.size}개 캐시가 초기화되었습니다")
        } catch (e: Exception) {
            CacheClearResult(cacheName = "ALL", success = false, message = "전체 캐시 초기화 실패: ${e.message}")
        }
    }

    fun getClearLogs(page: Int, size: Int): List<CacheClearLogResponse> {
        val pageable = PageRequest.of(page, size)
        return cacheClearLogRepository.findAllByOrderByClearedAtDesc(pageable)
            .content
            .map { log ->
                CacheClearLogResponse(
                    id = log.id,
                    cacheName = log.cacheName,
                    clearedBy = log.clearedBy,
                    clearedAt = log.clearedAt,
                    reason = log.reason
                )
            }
    }
}
