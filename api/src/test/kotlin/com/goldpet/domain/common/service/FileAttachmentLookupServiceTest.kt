package com.goldpet.domain.common.service

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.common.entity.FileAttachment
import com.goldpet.domain.common.repository.FileAttachmentRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class FileAttachmentLookupServiceTest : IntegrationTestBase() {

    @Autowired
    lateinit var lookup: FileAttachmentLookupService

    @Autowired
    lateinit var repo: FileAttachmentRepository

    private val seededUrls = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        lookup.invalidateAll()
        if (seededUrls.isNotEmpty()) {
            val ids = repo.findAllByUrlIn(seededUrls).map { it.id }
            if (ids.isNotEmpty()) repo.deleteAllById(ids)
            seededUrls.clear()
        }
    }

    private fun seed(attachment: FileAttachment): FileAttachment {
        seededUrls += attachment.url
        return repo.save(attachment)
    }

    @Test
    fun `batchLookup 100 URL 에 대해 단일 IN 쿼리로 실행된다`() {
        // given: 서로 다른 FileAttachment 100개 seed
        val prefix = "https://test.example/batchlookup-${UUID.randomUUID()}"
        val urls = (1..100).map { idx ->
            val saved = seed(
                FileAttachment(
                    ownerUserId = 1L,
                    fileType = "IMAGE",
                    mimeType = "image/jpeg",
                    url = "$prefix-$idx.jpg",
                    viewerUrl = "$prefix-${idx}_viewer.jpg",
                    thumbnailUrl = "$prefix-${idx}_thumb.jpg",
                    mediumUrl = "$prefix-${idx}_medium.jpg"
                )
            )
            saved.url
        }
        repo.flush()
        lookup.invalidateAll()

        // when/then: Caffeine 비어있는 상태에서 호출 — IN 쿼리 1회만 실행되는지
        val result = withinStatementBudget(max = 1) {
            lookup.batchLookup(urls)
        }

        assertEquals(urls.size, result.size)
    }

    @Test
    fun `batchLookup 후 viewerUrlFor 는 Caffeine hit 로 DB 미조회`() {
        // given
        val url = "https://test.example/cache-warm-${UUID.randomUUID()}.jpg"
        val viewerUrl = "${url.removeSuffix(".jpg")}_viewer.jpg"
        seed(
            FileAttachment(
                ownerUserId = 1L,
                fileType = "IMAGE",
                mimeType = "image/jpeg",
                url = url,
                viewerUrl = viewerUrl
            )
        )
        repo.flush()

        // when: batch 로 캐시 warm
        lookup.batchLookup(listOf(url))

        // then: 후속 호출이 DB hit 0 회
        val resolvedUrl = withinStatementBudget(max = 0) {
            lookup.viewerUrlFor(url)
        }
        assertEquals(viewerUrl, resolvedUrl)
    }

    @Test
    fun `viewerUrlFor 매칭 없으면 원본 URL 반환`() {
        val url = "https://test.example/no-attachment-${UUID.randomUUID()}.jpg"
        // DB 에 없는 URL
        val result = lookup.viewerUrlFor(url)
        assertEquals(url, result)
    }

    @Test
    fun `viewerUrlFor viewer NULL 시 medium → thumbnail → 원본 순서 fallback`() {
        val url = "https://test.example/fallback-${UUID.randomUUID()}.jpg"
        seed(
            FileAttachment(
                ownerUserId = 1L,
                fileType = "IMAGE",
                mimeType = "image/jpeg",
                url = url,
                thumbnailUrl = "$url.thumb",
                mediumUrl = "$url.medium",
                viewerUrl = null
            )
        )
        repo.flush()
        lookup.invalidateAll()

        val medium = lookup.viewerUrlFor(url)
        assertEquals("$url.medium", medium)
    }

    @Test
    fun `resolveMany 빈 리스트는 쿼리 없이 empty 반환`() {
        val result = withinStatementBudget(max = 0) {
            lookup.resolveMany(emptyList(), 1L)
        }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolveMany 100 ids 를 단일 chunk(500) 로 묶어 IN 쿼리 1회 실행`() {
        val prefix = "https://test.example/resolvemany-${UUID.randomUUID()}"
        val ids = (1..100).map { idx ->
            seed(
                FileAttachment(
                    ownerUserId = 1L,
                    fileType = "IMAGE",
                    mimeType = "image/jpeg",
                    url = "$prefix-$idx.jpg",
                    thumbnailUrl = "$prefix-${idx}_thumb.jpg",
                    mediumUrl = "$prefix-${idx}_medium.jpg",
                    viewerUrl = "$prefix-${idx}_viewer.jpg"
                )
            ).id
        }
        repo.flush()
        lookup.invalidateAll()

        val result = withinStatementBudget(max = 1) {
            lookup.resolveMany(ids, 1L)
        }

        assertEquals(ids.size, result.size)
        val sample = result[ids.first()]!!
        assertEquals("$prefix-1_viewer.jpg", sample.viewerUrl)
        assertEquals("$prefix-1_thumb.jpg", sample.thumbnailUrl)
    }

    @Test
    fun `resolveMany 1000 ids 는 500 chunked 로 2 쿼리 이하`() {
        val prefix = "https://test.example/resolvemany1000-${UUID.randomUUID()}"
        val ids = (1..1000).map { idx ->
            seed(
                FileAttachment(
                    ownerUserId = 1L,
                    fileType = "IMAGE",
                    mimeType = "image/jpeg",
                    url = "$prefix-$idx.jpg"
                )
            ).id
        }
        repo.flush()
        lookup.invalidateAll()

        val result = withinStatementBudget(max = 2) {
            lookup.resolveMany(ids, 1L)
        }
        assertEquals(ids.size, result.size)
    }

    @Test
    fun `resolveMany 1001 ids 는 IllegalArgumentException`() {
        val ids = (1L..1001L).toList()
        assertThrows(IllegalArgumentException::class.java) {
            lookup.resolveMany(ids, 1L)
        }
    }

    @Test
    fun `resolveMany 중복 id 는 distinct 처리 후 단일 query`() {
        val url = "https://test.example/resolvemany-dup-${UUID.randomUUID()}.jpg"
        val saved = seed(
            FileAttachment(
                ownerUserId = 1L,
                fileType = "IMAGE",
                mimeType = "image/jpeg",
                url = url
            )
        )
        repo.flush()
        lookup.invalidateAll()

        val result = withinStatementBudget(max = 1) {
            lookup.resolveMany(listOf(saved.id, saved.id, saved.id), 1L)
        }
        assertEquals(1, result.size)
    }

    @Test
    fun `invalidate 후 DB 재조회 수행`() {
        val url = "https://test.example/invalidate-${UUID.randomUUID()}.jpg"
        seed(
            FileAttachment(
                ownerUserId = 1L,
                fileType = "IMAGE",
                mimeType = "image/jpeg",
                url = url,
                viewerUrl = "$url.viewer"
            )
        )
        repo.flush()
        lookup.invalidateAll()

        // warm
        assertNotNull(lookup.viewerUrlFor(url))
        // invalidate
        lookup.invalidate(url)
        // 이제 다시 DB hit 필요
        val reResolved = withinStatementBudget(max = 1) {
            lookup.viewerUrlFor(url)
        }
        assertEquals("$url.viewer", reResolved)
    }
}
