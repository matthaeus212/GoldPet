package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.AdminEmoticonService
import com.goldpet.domain.admin.service.CreateEmoticonRequest
import com.goldpet.domain.admin.service.UpdateEmoticonRequest
import com.goldpet.domain.emoticon.dto.EmoticonPackResponse
import com.goldpet.domain.emoticon.dto.EmoticonResponse
import com.goldpet.domain.emoticon.service.EmoticonSeedS3MigrationService
import com.goldpet.domain.file.service.FileBackfillService
import com.goldpet.domain.file.service.GifBackfillResult
import org.springframework.cache.annotation.CacheEvict
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "어드민: 이모티콘 관리", description = "이모티콘 및 이모티콘 팩 CRUD")
@RestController
@RequestMapping("/api/v1/admin/emoticons")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminEmoticonController(
    private val adminEmoticonService: AdminEmoticonService,
    private val emoticonSeedS3MigrationService: EmoticonSeedS3MigrationService,
    // ARCH-004: GIF 변형 백필은 FileBackfillService 소관
    private val fileBackfillService: FileBackfillService
) {
    @Operation(summary = "전체 이모티콘 목록 조회")
    @GetMapping
    fun getAllEmoticons(): ResponseEntity<List<EmoticonResponse>> {
        return ResponseEntity.ok(adminEmoticonService.getAllEmoticons())
    }

    @Operation(summary = "이모티콘 팩 목록 조회")
    @GetMapping("/packs")
    fun getAllPacks(): ResponseEntity<List<EmoticonPackResponse>> {
        return ResponseEntity.ok(adminEmoticonService.getAllPacks())
    }

    @Operation(summary = "이모티콘 생성")
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createEmoticon(@RequestBody request: CreateEmoticonRequest): ResponseEntity<EmoticonResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminEmoticonService.createEmoticon(request))
    }

    @Operation(summary = "이모티콘 수정")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updateEmoticon(
        @PathVariable id: Long,
        @RequestBody request: UpdateEmoticonRequest
    ): ResponseEntity<EmoticonResponse> {
        return ResponseEntity.ok(adminEmoticonService.updateEmoticon(id, request))
    }

    @Operation(summary = "이모티콘 삭제")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteEmoticon(@PathVariable id: Long): ResponseEntity<Void> {
        adminEmoticonService.deleteEmoticon(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "이모티콘 활성/비활성 토글")
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun toggleActive(@PathVariable id: Long): ResponseEntity<EmoticonResponse> {
        return ResponseEntity.ok(adminEmoticonService.toggleActive(id))
    }

    @Operation(
        summary = "이모티콘 S3 마이그레이션 수동 실행 (T1-9)",
        description = "기존 V32 seed 이모티콘(정적 경로) 을 S3 PUBLIC 버킷으로 이관. 멱등. " +
            "기동 시 ApplicationReadyEvent 로 1회 자동 실행되지만 실패/재실행용으로 노출."
    )
    @PostMapping("/migrate-to-s3")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun migrateToS3(): ResponseEntity<EmoticonSeedS3MigrationService.Result> {
        return ResponseEntity.ok(emoticonSeedS3MigrationService.migrateAll())
    }

    @Operation(
        summary = "이모티콘 S3 자동 마이그레이션 중단",
        description = "image.emoticon.migration.enabled flag 를 false 로 설정. " +
            "기동 시 자동 재실행을 끈다 (수동 migrate-to-s3 는 여전히 호출 가능)."
    )
    @PostMapping("/migrate-to-s3/stop")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun disableMigration(): ResponseEntity<Void> {
        emoticonSeedS3MigrationService.disable()
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "GIF 이모티콘 first-frame variant 1회성 백필",
        description = "T-chat-latency-v2: generateVariants 가 GIF 를 skip 하던 탓에 " +
            "이모티콘 picker 첫 오픈이 느림. file_attachments 중 image/gif + thumbnailUrl=NULL 인 row 에 " +
            "대해 first-frame JPEG thumbnail/medium/viewer 를 생성하고 URL 기록. " +
            "성공 후 `emoticons` 캐시 evict 로 즉시 반영."
    )
    @PostMapping("/backfill-gif-variants")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    @CacheEvict(value = ["emoticons"], allEntries = true)
    fun backfillGifVariants(): ResponseEntity<GifBackfillResult> {
        return ResponseEntity.ok(fileBackfillService.backfillGifImageVariants())
    }
}
