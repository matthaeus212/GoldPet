package com.goldpet.domain.admin.controller

import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.file.service.FileBackfillService
import com.goldpet.domain.file.service.FileService
import com.goldpet.domain.file.service.WebPBackfillService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.net.URI

data class AdminFileUploadResponse(
    val id: Long,
    val url: String,
    val thumbnailUrl: String?,
    val mediumUrl: String?,
    val originalFileName: String?
)

@Tag(name = "어드민: 파일 관리", description = "어드민 파일 업로드 및 이미지 변형 백필")
@RestController
@RequestMapping("/api/v1/admin/files")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminFileController(
    private val fileService: FileService,
    // ARCH-004: 백필 배치는 FileBackfillService 로 분리됨 (업로드/스토리지 핵심과 관심사 분리)
    private val fileBackfillService: FileBackfillService,
    private val webPBackfillService: WebPBackfillService
) {
    @Operation(summary = "어드민 파일 업로드")
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun uploadFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("category", required = false, defaultValue = "notice") category: String,
        @AuthenticationPrincipal principal: AdminUserPrincipal
    ): ResponseEntity<AdminFileUploadResponse> {
        val fileAttachment = fileService.storeFile(file, principal.id, category)
        val response = AdminFileUploadResponse(
            id = fileAttachment.id,
            url = fileAttachment.url,
            thumbnailUrl = fileAttachment.thumbnailUrl,
            mediumUrl = fileAttachment.mediumUrl,
            originalFileName = fileAttachment.originalFileName
        )
        return ResponseEntity.created(URI.create(fileAttachment.url)).body(response)
    }

    @Operation(summary = "파일 이미지 변형 일괄 생성")
    @PostMapping("/backfill-variants")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun backfillVariants(
        @RequestParam(defaultValue = "100") batchSize: Int,
        @AuthenticationPrincipal principal: AdminUserPrincipal?
    ): ResponseEntity<FileBackfillService.BackfillResult> {
        principal ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val effectiveBatchSize = batchSize.coerceIn(1, 500)
        val result = fileBackfillService.backfillVariants(effectiveBatchSize)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "스코프 지정 백필", description = "scope=ALL|FILE_ATTACHMENTS|FILE_ATTACHMENTS_RETRY|WALK_SPOTS")
    @PostMapping("/backfill")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun backfillScoped(
        @RequestParam scope: FileBackfillService.BackfillScope,
        @RequestParam(defaultValue = "200") batchSize: Int,
        @RequestParam(defaultValue = "false") dryRun: Boolean,
        @AuthenticationPrincipal principal: AdminUserPrincipal?
    ): ResponseEntity<FileBackfillService.BackfillResult> {
        principal ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val effectiveBatchSize = batchSize.coerceIn(1, 500)
        val result = fileBackfillService.backfillVariants(scope, effectiveBatchSize, dryRun)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "재처리 백필 중지", description = "image.backfill.retry_enabled = false")
    @PostMapping("/backfill/stop")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun stopBackfillRetry(
        @AuthenticationPrincipal principal: AdminUserPrincipal?
    ): ResponseEntity<Map<String, String>> {
        principal ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        fileBackfillService.stopBackfillRetry()
        return ResponseEntity.ok(mapOf("status" to "stopped", "flag" to FileBackfillService.RETRY_ENABLED_KEY))
    }

    @Operation(
        summary = "WebP variant 일괄 백필",
        description = "thumbnail_url IS NOT NULL AND thumbnail_url_webp IS NULL 인 row에 WebP variant 생성. " +
            "dryRun=true(기본값)이면 S3/DB 쓰기 없이 통계만 반환."
    )
    @PostMapping("/webp-backfill")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun backfillWebpVariants(
        @RequestParam(defaultValue = "true") dryRun: Boolean,
        @RequestParam(defaultValue = "100") batchSize: Int,
        @AuthenticationPrincipal principal: AdminUserPrincipal?
    ): ResponseEntity<WebPBackfillService.BackfillResult> {
        principal ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val result = webPBackfillService.backfillBatch(dryRun = dryRun, batchSize = batchSize)
        return ResponseEntity.ok(result)
    }
}
