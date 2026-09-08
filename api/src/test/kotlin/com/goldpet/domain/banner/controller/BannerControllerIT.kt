package com.goldpet.domain.banner.controller

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.admin.entity.Banner
import com.goldpet.domain.admin.entity.BannerPlacement
import com.goldpet.domain.admin.repository.BannerRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * HTTP contract tests for the public GET /api/v1/banners.
 *
 * Verifies: permitAll(200 anon), named DTO 4 fields, internal field non-leak,
 * empty placement → [], active/period-window filtering, Cache-Control + ETag/304.
 */
@AutoConfigureMockMvc
@Tag("integration")
class BannerControllerIT : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var bannerRepository: BannerRepository

    private val saved = mutableListOf<Banner>()

    @BeforeEach
    fun setUp() {
        // HOME active (no window)
        saved += bannerRepository.save(
            Banner(title = "home-active", imageUrl = null, link = "/home", placement = BannerPlacement.HOME)
        )
        // CHAT inactive → excluded
        saved += bannerRepository.save(
            Banner(title = "chat-inactive", isActive = false, placement = BannerPlacement.CHAT)
        )
        // WALK expired (endDate in past) → excluded
        saved += bannerRepository.save(
            Banner(
                title = "walk-expired",
                placement = BannerPlacement.WALK,
                endDate = LocalDateTime.now().minusDays(1)
            )
        )
    }

    @AfterEach
    fun tearDown() {
        saved.forEach { runCatching { bannerRepository.deleteById(it.id) } }
        saved.clear()
    }

    @Test
    fun `GET banners returns 200 for anonymous with named DTO 4 fields`() {
        mockMvc.perform(get("/api/v1/banners"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.home").isArray)
            .andExpect(jsonPath("$.chat").isArray)
            .andExpect(jsonPath("$.walk").isArray)
            .andExpect(jsonPath("$.community").isArray)
    }

    @Test
    fun `GET banners excludes inactive and expired, includes active`() {
        mockMvc.perform(get("/api/v1/banners"))
            .andExpect(status().isOk)
            // HOME active present
            .andExpect(jsonPath("$.home[?(@.title == 'home-active')]").exists())
            // CHAT inactive excluded → empty array
            .andExpect(jsonPath("$.chat").isEmpty)
            // WALK expired excluded → empty array
            .andExpect(jsonPath("$.walk").isEmpty)
            // COMMUNITY never populated → empty array (key present, not missing)
            .andExpect(jsonPath("$.community").isEmpty)
    }

    @Test
    fun `GET banners does not leak admin internal fields`() {
        mockMvc.perform(get("/api/v1/banners"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.home[0].isActive").doesNotExist())
            .andExpect(jsonPath("$.home[0].displayOrder").doesNotExist())
            .andExpect(jsonPath("$.home[0].startDate").doesNotExist())
            .andExpect(jsonPath("$.home[0].endDate").doesNotExist())
            .andExpect(jsonPath("$.home[0].placement").value("HOME"))
    }

    @Test
    fun `GET banners sets Cache-Control no-cache and ETag, returns 304 on If-None-Match`() {
        // no-cache: 매 요청 재검증(admin 비활성화가 클라 캐시에 묶이지 않도록). 미변경 시 ETag 304.
        val result = mockMvc.perform(get("/api/v1/banners"))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
            .andExpect(header().exists("ETag"))
            .andReturn()

        val etag = result.response.getHeader("ETag")!!
        mockMvc.perform(get("/api/v1/banners").header("If-None-Match", etag))
            .andExpect(status().isNotModified)
    }
}
