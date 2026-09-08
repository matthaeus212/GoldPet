package com.goldpet.domain.common.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.goldpet.domain.chat.repository.ChatMessageRepository
import com.goldpet.domain.common.entity.FileAttachment
import com.goldpet.domain.common.repository.FileAttachmentRepository
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * T1-1.1 — URL → FileAttachment variant lookup with Caffeine cache.
 *
 * ## 설계
 * - 60s Caffeine TTL + 10k entry bound. 리스트 DTO 빌드 시 서비스 레이어에서
 *   `batchLookup(urls)` 로 미리 prefetch 해두면 후속 `viewerUrlFor` 는 전부 캐시 hit.
 * - Emoticon/Pet profile 이미지 변경 같은 수명 짧은 mutation 경로에서는 `invalidate(url)` 로 수동 제거.
 *
 * ## N+1 방지 규약 (계획 Section 3 T1-1)
 * 1. DTO mapper 내부에서 `viewerUrlFor` / `thumbnailUrlFor` 만 호출 — 단일 lookup 을 캐시로 흡수.
 * 2. 서비스 레이어가 **먼저** `batchLookup(allUrls)` 로 IN 쿼리 1회 실행 → Caffeine 에 warm.
 * 3. 통합 테스트는 `IntegrationTestBase.withinStatementBudget(max = 1)` 로 단일 SELECT 강제.
 *
 * ## Scope
 * - Public bucket URL (`https://s3.../goldpet-public/{uuid}.jpg`) — 안정적, 캐시 최적.
 * - Private bucket presigned URL (`?X-Amz-Signature=...`) — 60m 만료이므로 60s TTL 이면 충돌 무.
 *   signature 가 재발급될 때는 URL 문자열 자체가 바뀌므로 자연 캐시 miss → 재조회.
 */
@Service
class FileAttachmentLookupService(
    private val fileAttachmentRepository: FileAttachmentRepository,
    // SEC-004 (W1a): resolveMany 접근권 판정(같은 방 참여자 열람 허용)에 사용.
    // TODO(W4/ARCH-002): common → chat 리포지토리 의존은 계층 역전. 아키텍처 웨이브에서
    // 도메인 서비스/이벤트 경유로 수렴. 보안 픽스 우선이라 현재는 직접 주입.
    private val chatMessageRepository: ChatMessageRepository,
) {
    private val cache: Cache<String, FileAttachment> = Caffeine.newBuilder()
        .maximumSize(MAX_CACHE_ENTRIES)
        .expireAfterWrite(Duration.ofSeconds(CACHE_TTL_SECONDS))
        .build()

    /**
     * 여러 URL 을 한 번의 IN 쿼리로 조회하고 Caffeine 에 warm.
     * DTO mapper 가 리스트 처리 시작 전에 서비스 레이어에서 반드시 1회 호출.
     *
     * @return 존재하는 FileAttachment 매핑. 조회 실패 URL 은 반환 Map 에서 누락.
     */
    fun batchLookup(urls: Collection<String?>): Map<String, FileAttachment> {
        val distinct = urls.asSequence()
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (distinct.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, FileAttachment>()
        val missing = mutableListOf<String>()
        for (u in distinct) {
            val hit = cache.getIfPresent(u)
            if (hit != null) result[u] = hit else missing += u
        }

        if (missing.isNotEmpty()) {
            val rows = fileAttachmentRepository.findAllByUrlIn(missing)
            for (fa in rows) {
                cache.put(fa.url, fa)
                result[fa.url] = fa
            }
        }
        return result
    }

    /**
     * 단일 URL → viewer variant. 없으면 medium → thumbnail → 원본 순서 fallback.
     * DTO mapper 내부에서 주로 호출. `batchLookup` prefetch 로 warm 해두는 게 필수.
     */
    fun viewerUrlFor(url: String?): String? {
        if (url.isNullOrBlank()) return url
        val fa = resolve(url) ?: return url
        return fa.viewerUrl ?: fa.mediumUrl ?: fa.thumbnailUrl ?: fa.url
    }

    fun mediumUrlFor(url: String?): String? {
        if (url.isNullOrBlank()) return url
        val fa = resolve(url) ?: return url
        return fa.mediumUrl ?: fa.thumbnailUrl ?: fa.url
    }

    fun thumbnailUrlFor(url: String?): String? {
        if (url.isNullOrBlank()) return url
        val fa = resolve(url) ?: return url
        return fa.thumbnailUrl ?: fa.url
    }

    /** T2: WebP thumbnail variant. WebP 미생성 행(구 업로드)은 null 반환 — 클라이언트 fallback 처리. */
    fun thumbnailUrlWebpFor(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return resolve(url)?.thumbnailUrlWebp
    }

    /** T2: WebP medium variant. */
    fun mediumUrlWebpFor(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return resolve(url)?.mediumUrlWebp
    }

    /** T2: WebP viewer variant. */
    fun viewerUrlWebpFor(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return resolve(url)?.viewerUrlWebp
    }

    /** URL 업데이트 이벤트 (Emoticon/Pet 프로필 변경 등) 에서 수동 호출. */
    fun invalidate(url: String) {
        cache.invalidate(url)
    }

    fun invalidateAll() {
        cache.invalidateAll()
    }

    /**
     * Batch resolve by ID (T-chat-latency-v2 Step 3).
     *
     * 프론트 핫패스 (채팅 상세 열람, 커뮤니티 피드) 가 fileId 여러 건의 최종 URL/variant 를
     * 한 번에 요구할 때 사용. 기존 `batchLookup(urls)` 는 URL → FA 캐시 warm 용이고
     * 이 메서드는 FA ID 기반 배치 조회 + 서브 variant URL 을 평면 DTO 로 반환한다.
     *
     * ## 쿼리 예산
     * - `CHUNK_SIZE` = 500 단위로 `findAllById` 한 번씩 실행 → 1000 IDs = 2 쿼리
     * - N+1 방지 규약 section 3 충족: DoD "1000개 id 호출 시 단건 1000회 대비 1/100 이하"
     *
     * ## 경계값
     * - 빈 리스트 → empty Map (쿼리 0)
     * - `MAX_RESOLVE_BATCH` = 1000 초과 시 IllegalArgumentException
     * - 누락 ID (soft-delete / 삭제된 row) 는 반환 Map 에 누락
     *
     * 반환된 Caffeine 캐시에도 `fa.url → fa` 로 warm 해 두어 후속 variant lookup 은 캐시 hit.
     *
     * ## 접근 제어 (SEC-004 / W1a)
     * [viewerUserId] 가 **소유자이거나**(ownerUserId 일치), 참여 중인 채팅방의 메시지가 참조하는
     * 첨부만 반환한다. 접근권 없는 id 는 결과 Map 에서 누락시켜 열거(enumeration)를 차단한다
     * (404/403 대신 조용히 제외 — 프론트는 누락 id 를 이미 optional 처리).
     */
    fun resolveMany(ids: List<Long>, viewerUserId: Long): Map<Long, ResolvedAttachment> {
        if (ids.isEmpty()) return emptyMap()
        val distinct = ids.distinct()
        require(distinct.size <= MAX_RESOLVE_BATCH) {
            "resolveMany: max $MAX_RESOLVE_BATCH ids per call (got ${distinct.size})"
        }

        // 1) id → FileAttachment 배치 조회 (500 chunked)
        val fetched = LinkedHashMap<Long, FileAttachment>(distinct.size)
        distinct.chunked(CHUNK_SIZE).forEach { chunk ->
            fileAttachmentRepository.findAllById(chunk).forEach { fetched[it.id] = it }
        }
        if (fetched.isEmpty()) return emptyMap()

        // 2) 접근권 판정: 소유자 OR 같은 방 참여자. 소유자가 아닌 것만 참여 검사 쿼리로 확인.
        val nonOwnedIds = fetched.values.asSequence()
            .filter { it.ownerUserId != viewerUserId }
            .map { it.id }
            .toList()
        val accessibleViaChat: Set<Long> = if (nonOwnedIds.isEmpty()) {
            emptySet()
        } else {
            chatMessageRepository.findAccessibleFileIds(viewerUserId, nonOwnedIds).toSet()
        }

        // 3) 접근 가능한 첨부만 결과에 담고 캐시 warm
        val result = LinkedHashMap<Long, ResolvedAttachment>(fetched.size)
        for ((id, fa) in fetched) {
            val allowed = fa.ownerUserId == viewerUserId || id in accessibleViaChat
            if (!allowed) continue
            cache.put(fa.url, fa) // warm URL-keyed cache for downstream variant resolvers
            result[id] = ResolvedAttachment.from(fa)
        }
        return result
    }

    /**
     * SEC-005 — presigned URL 발급 전 소유권/접근권 판정.
     *
     * [fileKey] 로 FileAttachment 를 찾아(원본/variant key 모두 대조) viewer 가 소유자이거나
     * 참여 중인 채팅방 메시지가 참조하는 첨부일 때만 해당 첨부를 반환한다. 그 외(미존재 포함)는
     * null 을 반환해 호출부가 403/404 로 거부하도록 한다.
     */
    fun authorizeKeyAccess(fileKey: String, viewerUserId: Long): FileAttachment? {
        if (fileKey.isBlank()) return null
        val fa = fileAttachmentRepository.findByAnyKey(fileKey).firstOrNull() ?: return null
        if (fa.ownerUserId == viewerUserId) return fa
        val accessible = chatMessageRepository.findAccessibleFileIds(viewerUserId, listOf(fa.id))
        return if (fa.id in accessible) fa else null
    }

    private fun resolve(url: String): FileAttachment? {
        val cached = cache.getIfPresent(url)
        if (cached != null) return cached
        val fromDb = fileAttachmentRepository.findByUrl(url) ?: return null
        cache.put(url, fromDb)
        return fromDb
    }

    companion object {
        private const val MAX_CACHE_ENTRIES: Long = 10_000
        private const val CACHE_TTL_SECONDS: Long = 60
        /** PostgreSQL IN-param safety + Hibernate batch tuning. */
        const val CHUNK_SIZE: Int = 500
        /** Upper bound for a single resolveMany call. Frontend ring-buffers beyond this. */
        const val MAX_RESOLVE_BATCH: Int = 1000
    }
}

data class ResolvedAttachment(
    val id: Long,
    val url: String,
    val thumbnailUrl: String?,
    val mediumUrl: String?,
    val viewerUrl: String?,
    /** T2: WebP thumbnail (200px). Null for rows uploaded before WebP pipeline. */
    val thumbnailUrlWebp: String? = null,
    /** T2: WebP medium (600px). Null for rows uploaded before WebP pipeline. */
    val mediumUrlWebp: String? = null,
    /** T2: WebP viewer (1600px). Null for rows uploaded before WebP pipeline. */
    val viewerUrlWebp: String? = null,
    val fileType: String,
    val mimeType: String,
    val originalFileName: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?
) {
    companion object {
        fun from(fa: FileAttachment): ResolvedAttachment = ResolvedAttachment(
            id = fa.id,
            url = fa.url,
            thumbnailUrl = fa.thumbnailUrl,
            mediumUrl = fa.mediumUrl,
            viewerUrl = fa.viewerUrl,
            thumbnailUrlWebp = fa.thumbnailUrlWebp,
            mediumUrlWebp = fa.mediumUrlWebp,
            viewerUrlWebp = fa.viewerUrlWebp,
            fileType = fa.fileType,
            mimeType = fa.mimeType,
            originalFileName = fa.originalFileName,
            sizeBytes = fa.sizeBytes,
            width = fa.width,
            height = fa.height
        )
    }
}
