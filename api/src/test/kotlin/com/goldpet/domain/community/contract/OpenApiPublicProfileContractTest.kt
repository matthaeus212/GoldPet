package com.goldpet.domain.community.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * OpenAPI contract: PII field names must be absent from public-facing DTO schemas.
 *
 * Validates the generated `/v3/api-docs` JSON to ensure that
 * `PublicUserProfileResponse` and `CommunityPostSummaryDto` do not accidentally
 * expose PII fields (phone, email, birth, gender, etc.) to external callers.
 *
 * This is the second layer of defence after `PublicFacingDtoArchTest` (compile-time ArchUnit).
 */
@AutoConfigureMockMvc
@Tag("integration")
class OpenApiPublicProfileContractTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    companion object {
        /**
         * Field names that must never appear in a public-facing DTO schema.
         * Mirrors the list in PublicFacingDtoArchTest + User entity PII columns.
         */
        private val PII_FIELDS = setOf(
            "phoneNumber", "email", "emailHash",
            "birthDate", "birthYear",
            "gender", "name", "password",
            "fcmToken",
            "mainLocationGeom", "mainLocationText",
            "profileLockedAt",
            "oauthProvider", "oauthId",
        )
    }

    @Test
    fun `OpenAPI schema for PublicUserProfileResponse contains no PII field names`() {
        val schemas = fetchOpenApiSchemas()

        val schema = schemas.path("PublicUserProfileResponse")
        assertTrue(!schema.isMissingNode) {
            "Schema 'PublicUserProfileResponse' not found in OpenAPI spec — check controller/DTO registration"
        }

        val properties = schema.path("properties").fieldNames().asSequence().toSet()
        val leaked = properties.intersect(PII_FIELDS)
        assertTrue(leaked.isEmpty()) {
            "PublicUserProfileResponse OpenAPI schema exposes PII field(s): $leaked"
        }
    }

    @Test
    fun `OpenAPI schema for CommunityPostSummaryDto contains no PII field names`() {
        val schemas = fetchOpenApiSchemas()

        val schema = schemas.path("CommunityPostSummaryDto")
        assertTrue(!schema.isMissingNode) {
            "Schema 'CommunityPostSummaryDto' not found in OpenAPI spec — check controller/DTO registration"
        }

        val properties = schema.path("properties").fieldNames().asSequence().toSet()
        val leaked = properties.intersect(PII_FIELDS)
        assertTrue(leaked.isEmpty()) {
            "CommunityPostSummaryDto OpenAPI schema exposes PII field(s): $leaked"
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private fun fetchOpenApiSchemas(): com.fasterxml.jackson.databind.JsonNode {
        val apiDocs = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        val root = objectMapper.readTree(apiDocs)
        return root.path("components").path("schemas")
    }
}
