package com.goldpet.domain.emoticon.service

import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.common.util.InMemoryMultipartFile
import com.goldpet.domain.emoticon.repository.EmoticonRepository
import com.goldpet.domain.file.service.FileService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cache.annotation.CacheEvict
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

/**
 * T1-9 Option C-Extended — 기존 V32 seed 이모티콘(frontend 정적 에셋)을 S3 PUBLIC 버킷으로 이관.
 *
 * - 기동 시 1회 자동 실행 (@EventListener(ApplicationReadyEvent))
 * - 멱등성: emoticon.imageUrl 이 이미 `http` 로 시작하면 skip
 * - classpath resource `seeds/emoticons/{code}.png` 로드 → FileService.storeFile(category="emoticon")
 *   → emoticons.image_url UPDATE
 * - Feature flag: `image.emoticon.migration.enabled` (기본 true; admin endpoint 로 중단 가능)
 *
 * Admin 엔드포인트(migrateToS3)에서도 수동 재실행 가능.
 */
@Service
class EmoticonSeedS3MigrationService(
    private val emoticonRepository: EmoticonRepository,
    private val fileService: FileService,
    private val systemSettingService: SystemSettingService,
    private val environment: Environment,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ENABLED_KEY = "image.emoticon.migration.enabled"
        const val SYSTEM_ADMIN_USER_ID = 1L
        const val SEED_CLASSPATH_DIR = "seeds/emoticons"
    }

    data class Result(val total: Int, val migrated: Int, val skipped: Int, val failed: Int)

    @EventListener(ApplicationReadyEvent::class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun onApplicationReady() {
        // codegen profile 은 Redis/MinIO 없는 minimal 환경. SystemSettingService(@Cacheable)
        // + FileService(S3) 모두 호출 불가이므로 자동 실행 skip.
        // Jenkins verifyOpenApiSpec 가 ApplicationReadyEvent 에서 실패하면 OpenAPI codegen
        // 파이프라인 전체가 깨진다.
        if (environment.activeProfiles.any { it.equals("codegen", ignoreCase = true) }) {
            log.info("Emoticon S3 migration skipped: codegen profile active")
            return
        }
        if (!systemSettingService.getBoolean(ENABLED_KEY, true)) {
            log.info("Emoticon S3 migration skipped: {}=false", ENABLED_KEY)
            return
        }
        val result = runCatching { migrateAll() }
            .onFailure { log.warn("Emoticon S3 migration failed: {}", it.message, it) }
            .getOrNull() ?: return
        log.info("Emoticon S3 migration: total={}, migrated={}, skipped={}, failed={}",
            result.total, result.migrated, result.skipped, result.failed)
    }

    @Transactional
    @CacheEvict(value = ["emoticons"], allEntries = true)
    fun migrateAll(): Result {
        val emoticons = emoticonRepository.findAll()
        var migrated = 0
        var skipped = 0
        var failed = 0
        for (emoticon in emoticons) {
            val code = emoticon.code
            // 멱등성: 이미 http 로 시작하면 이관 완료 상태
            if (emoticon.imageUrl.startsWith("http")) {
                skipped++
                continue
            }
            if (code.isNullOrBlank()) {
                log.warn("Emoticon id={} has no code, cannot locate seed file. skip.", emoticon.id)
                skipped++
                continue
            }
            val resourcePath = "$SEED_CLASSPATH_DIR/$code.png"
            val resource = ClassPathResource(resourcePath)
            if (!resource.exists()) {
                log.warn("Seed file not found: {} (emoticon id={}, code={}). skip.",
                    resourcePath, emoticon.id, code)
                skipped++
                continue
            }
            try {
                val bytes = resource.inputStream.use { it.readBytes() }
                val multipart: MultipartFile = InMemoryMultipartFile(
                    bytes = bytes,
                    originalFilename = "$code.png",
                    mimeType = "image/png"
                )
                val stored = fileService.storeFile(
                    file = multipart,
                    userId = SYSTEM_ADMIN_USER_ID,
                    category = "emoticon"
                )
                emoticon.imageUrl = stored.url
                emoticonRepository.save(emoticon)
                migrated++
                log.info("Emoticon migrated to S3: id={}, code={}, url={}", emoticon.id, code, stored.url)
            } catch (e: Exception) {
                log.warn("Emoticon migration failed: id={}, code={}: {}", emoticon.id, code, e.message, e)
                failed++
            }
        }
        return Result(total = emoticons.size, migrated = migrated, skipped = skipped, failed = failed)
    }

    /**
     * 관리자 강제 중단 — 기동 시 자동 재실행을 끈다 (admin 이 다시 true 로 돌릴 때까지).
     */
    fun disable() {
        systemSettingService.setValue(
            ENABLED_KEY,
            "false",
            description = "Emoticon seed S3 migration kill switch (T1-9). true 로 설정 시 다음 기동에 재실행."
        )
    }
}

