package com.goldpet.domain.walk.contract

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
 * OpenAPI contract: WalkResponse schema must remain stable.
 *
 * Validates the generated `/v3/api-docs` JSON to ensure that `WalkResponse`
 * is registered and contains the fields that external callers depend on.
 *
 * Detects OpenAPI drift: if a field is accidentally renamed or removed from
 * the DTO, this test will fail before the change reaches production.
 *
 * Note: The actual walk record response DTO is `WalkResponse` (in WalkDto.kt).
 * There is no separate `WalkRecordResponse` class in this codebase.
 */
@AutoConfigureMockMvc
@Tag("integration")
class WalkRecordContractTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    companion object {
        /**
         * Fields that external clients depend on from WalkResponse.
         * Removing or renaming any of these is a breaking API change.
         */
        private val WALK_RESPONSE_REQUIRED_FIELDS = setOf(
            "id", "userId", "startTime", "endTime",
            "distanceKm", "durationSeconds", "path", "spots",
            "isPublic", "petNames", "petIds"
        )
    }

    @Test
    fun `OpenAPI schema for WalkResponse contains all required fields`() {
        val schemas = fetchOpenApiSchemas()

        val schema = schemas.path("WalkResponse")
        assertTrue(!schema.isMissingNode) {
            "Schema 'WalkResponse' not found in OpenAPI spec — check controller/DTO registration"
        }

        val properties = schema.path("properties").fieldNames().asSequence().toSet()
        val missing = WALK_RESPONSE_REQUIRED_FIELDS - properties
        assertTrue(missing.isEmpty()) {
            "WalkResponse OpenAPI schema is missing required field(s): $missing"
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
