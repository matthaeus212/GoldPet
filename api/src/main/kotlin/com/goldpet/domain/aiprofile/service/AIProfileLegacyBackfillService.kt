package com.goldpet.domain.aiprofile.service

import com.goldpet.domain.aiprofile.repository.AIProfileRequestRepository
import com.goldpet.domain.common.entity.FileAttachment
import com.goldpet.domain.common.repository.FileAttachmentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * T1-7b — T1-7a 배포 이전 생성된 AI 프로필 결과 URL(`ai-profiles/{id}.jpg` 형식)은
 * FileAttachment 로우 없이 S3 에만 파일이 있어 T1-1.x variant lookup 이 miss 한다.
 * 이 서비스는 레거시 resultUrl 에 대해 FileAttachment 로우를 역삽입한다.
 *
 * Variant(thumb/medium/viewer) 는 이 서비스가 직접 만들지 않는다. 역삽입 후에는
 * `findImagesWithoutVariants` 쿼리가 해당 row 를 잡으므로 기존
 * `POST /api/v1/admin/files/backfill?scope=FILE_ATTACHMENTS` 를 이어서 호출하면
 * async variant 생성 파이프라인이 자동으로 처리한다.
 *
 * ## 멱등성
 * - `fileAttachmentRepository.findByUrl(legacyUrl)` 로 이미 등록된 row 는 skip.
 * - 레거시 URL 을 그대로 `FileAttachment.url` 로 저장하기 때문에 Pet 복제
 *   (AIProfileService.kt) 경로의 URL 문자열 비교가 유지된다.
 */
@Service
class AIProfileLegacyBackfillService(
    private val aiProfileRequestRepository: AIProfileRequestRepository,
    private val fileAttachmentRepository: FileAttachmentRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Result(
        val total: Int,
        val inserted: Int,
        val alreadyLinked: Int,
        val skipped: Int,
    )

    @Transactional
    fun backfillLegacyAttachments(dryRun: Boolean = false): Result {
        val candidates = aiProfileRequestRepository.findAll()
            .filter { it.resultUrl?.contains("/ai-profiles/") == true }

        var inserted = 0
        var alreadyLinked = 0
        var skipped = 0

        for (req in candidates) {
            val url = req.resultUrl
            if (url == null) {
                skipped++
                continue
            }
            val existing = fileAttachmentRepository.findByUrl(url)
            if (existing != null) {
                alreadyLinked++
                continue
            }
            if (dryRun) {
                inserted++
                continue
            }
            try {
                fileAttachmentRepository.save(
                    FileAttachment(
                        ownerUserId = req.user.id,
                        fileType = "IMAGE",
                        mimeType = "image/jpeg",
                        url = url,
                        originalFileName = "ai-profile-${req.id}.jpg",
                        sizeBytes = null,
                        width = 512,
                        height = 512,
                    ),
                )
                inserted++
                log.info("Legacy AI profile FileAttachment inserted: requestId={}, url={}", req.id, url)
            } catch (e: Exception) {
                log.warn("Failed to insert FileAttachment for requestId={}: {}", req.id, e.message, e)
                skipped++
            }
        }

        return Result(
            total = candidates.size,
            inserted = inserted,
            alreadyLinked = alreadyLinked,
            skipped = skipped,
        )
    }
}
