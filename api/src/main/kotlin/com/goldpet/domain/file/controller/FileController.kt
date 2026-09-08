package com.goldpet.domain.file.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.common.service.ResolvedAttachment
import com.goldpet.domain.file.service.FileService
import io.micrometer.core.instrument.MeterRegistry
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.net.URI

data class FileUploadResponse(
    val id: Long,
    val url: String,
    val thumbnailUrl: String?,
    val mediumUrl: String?,
    /** T1-5: viewer variant (1600px). async variant-gen 완료 전에는 null. 클라이언트는
     *  업로드 직후 DOM 에 url 사용, 이후 재조회 시 viewerUrl 로 전환 가능. */
    val viewerUrl: String?,
    /** T2: WebP thumbnail (200px). async variant-gen 완료 전에는 null. */
    val thumbnailUrlWebp: String? = null,
    /** T2: WebP medium (600px). async variant-gen 완료 전에는 null. */
    val mediumUrlWebp: String? = null,
    /** T2: WebP viewer (1600px). async variant-gen 완료 전에는 null. */
    val viewerUrlWebp: String? = null,
    val fileType: String,
    val mimeType: String,
    val originalFileName: String?,
    val width: Int?,
    val height: Int?
)

data class PresignedUrlResponse(
    val url: String
)

/**
 * T-chat-latency-v2 Step 3 — batch resolve request body.
 * 최대 1000개 (`FileAttachmentLookupService.MAX_RESOLVE_BATCH`) 까지 허용.
 */
data class ResolveManyRequest(
    @field:NotEmpty(message = "ids 는 비어있을 수 없습니다")
    @field:Size(max = 1000, message = "ids 는 1000개를 초과할 수 없습니다")
    val ids: List<Long> = emptyList()
)

data class ResolveManyResponse(
    val attachments: Map<Long, ResolvedAttachment>
)

@Tag(name = "파일", description = "파일 업로드 및 다운로드")
@RestController
@RequestMapping("/api/v1/files")
class FileController(
    private val fileService: FileService,
    private val meterRegistry: MeterRegistry,
    private val fileAttachmentLookupService: FileAttachmentLookupService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Upload a file. Pass `category` to control bucket routing:
     * - "profile", "pet", "avatar" -> public bucket (returns direct URL)
     * - omitted or any other value -> private bucket (returns file key; use /files/{key}/url to access)
     */
    @Operation(summary = "파일 업로드")
    @PostMapping("/upload")
    fun uploadFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("category", required = false) category: String?,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<FileUploadResponse> {
        val fileAttachment = fileService.storeFile(file, principal.id, category)
        val response = FileUploadResponse(
            id = fileAttachment.id,
            url = fileAttachment.url,
            thumbnailUrl = fileAttachment.thumbnailUrl,
            mediumUrl = fileAttachment.mediumUrl,
            viewerUrl = fileAttachment.viewerUrl,
            thumbnailUrlWebp = fileAttachment.thumbnailUrlWebp,
            mediumUrlWebp = fileAttachment.mediumUrlWebp,
            viewerUrlWebp = fileAttachment.viewerUrlWebp,
            fileType = fileAttachment.fileType,
            mimeType = fileAttachment.mimeType,
            originalFileName = fileAttachment.originalFileName,
            width = fileAttachment.width,
            height = fileAttachment.height
        )
        return ResponseEntity.created(URI.create(fileAttachment.url)).body(response)
    }

    /**
     * **GONE (T1-4 2단계)** — 비공개 파일 streaming 프록시 영구 폐기.
     *
     * 1단계(d3c9d22)에서 Counter + @Deprecated 적용 후 TestFlight 관측에서 0 hit
     * 확인 → 2단계에서 HTTP 410 Gone 전환. 클라이언트는 `GET /{fileKey}/url`
     * (presigned URL) 사용. Counter 는 유지하여 stale bundle hit 감지.
     */
    @Deprecated(
        message = "410 Gone 응답. presigned URL (/{fileKey}/url) 사용.",
        replaceWith = ReplaceWith("getPresignedUrl(fileKey, 60, principal)"),
        level = DeprecationLevel.WARNING
    )
    @Operation(
        summary = "[GONE] 비공개 파일 스트리밍 다운로드 — 410 Gone 반환",
        description = "T1-4 로 영구 폐기. 모든 요청에 대해 HTTP 410 Gone 응답. presigned URL (/{fileKey}/url) 사용.",
        deprecated = true
    )
    @GetMapping("/{fileKey}/content")
    fun getFileContent(
        @PathVariable fileKey: String,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): ResponseEntity<Map<String, String>> {
        // SEC-006/EXT-CDX-010: user_id 를 메트릭 태그(무한 카디널리티 + PII)로 넣지 않는다.
        // 개별 사용자 식별이 필요하면 로그로만 남기고 메트릭은 태그 없이 집계.
        meterRegistry.counter("file_proxy_stream_total").increment()
        val userTag = principal?.id?.toString() ?: "unknown"
        log.warn(
            "legacy /files/{}/content proxy hit after 410 transition (user_id={}). Stale bundle suspected.",
            fileKey, userTag
        )
        return ResponseEntity.status(HttpStatus.GONE).body(
            mapOf(
                "error" to "GONE",
                "message" to "proxy endpoint removed. Use GET /api/v1/files/{fileKey}/url for presigned URL."
            )
        )
    }

    @Operation(summary = "비공개 파일 Presigned URL 발급")
    @GetMapping("/{fileKey}/url")
    fun getPresignedUrl(
        @PathVariable fileKey: String,
        @RequestParam("expirationMinutes", required = false, defaultValue = "60") expirationMinutes: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PresignedUrlResponse> {
        // SEC-005 (+EXT-CDX-004): fileKey 만으로 서명하지 않는다. 소유자이거나 참여 중인 채팅방이
        // 참조하는 첨부일 때만 발급. 접근권 없으면 404(존재 은폐 — fileKey 는 UUID 라 열거 어려움).
        fileAttachmentLookupService.authorizeKeyAccess(fileKey, principal.id)
            ?: throw com.goldpet.domain.common.exception.NotFoundException("파일을 찾을 수 없습니다.")
        // 만료 상한은 FileService.getPresignedUrl 에서 서버측으로 clamp.
        val url = fileService.getPresignedUrl(fileKey, expirationMinutes)
        return ResponseEntity.ok(PresignedUrlResponse(url = url))
    }

    /**
     * T-chat-latency-v2 Step 3 — 복수 fileId 를 한 번에 variant URL 까지 평면 DTO 로 해석.
     *
     * 단건 `GET /{fileKey}/url` 은 fileKey(String) 기반이라 IN 쿼리로 묶을 수 없고, 본 엔드포인트는
     * 엔티티 id(Long) 기반이므로 신규 경로로 분리. 내부에서 IN 쿼리 1회 (500 chunked) 로 처리해
     * 단건 1000회 대비 쿼리수 1/500 이하.
     */
    @Operation(summary = "파일 attachment 배치 조회 (id 기반, variant URL 포함)")
    @PostMapping("/resolve")
    fun resolveMany(
        @Valid @RequestBody request: ResolveManyRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ResolveManyResponse> {
        // SEC-004 (W1a): viewer 소유/참여 필터. 접근권 없는 id 는 응답에서 누락(열거 차단).
        val attachments = fileAttachmentLookupService.resolveMany(request.ids, principal.id)
        return ResponseEntity.ok(ResolveManyResponse(attachments = attachments))
    }
}
