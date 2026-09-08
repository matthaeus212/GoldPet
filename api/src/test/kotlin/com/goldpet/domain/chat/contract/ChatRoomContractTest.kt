package com.goldpet.domain.chat.contract

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
 * OpenAPI contract: ChatRoomResponse and ChatMessageResponse schemas must remain stable.
 *
 * Validates the generated `/v3/api-docs` JSON to ensure that
 * `ChatRoomResponse` and `ChatMessageResponse` are registered and contain
 * the fields that external callers depend on.
 *
 * Detects OpenAPI drift: if a field is accidentally renamed or removed from
 * the DTO, this test will fail before the change reaches production.
 */
@AutoConfigureMockMvc
@Tag("integration")
class ChatRoomContractTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    companion object {
        /**
         * Fields that external clients depend on from ChatRoomResponse.
         * Removing or renaming any of these is a breaking API change.
         */
        private val CHAT_ROOM_REQUIRED_FIELDS = setOf(
            "id", "roomType", "name", "participants",
            "lastMessage", "lastMessageTime", "unreadCount", "isGroup"
        )

        /**
         * Fields that external clients depend on from ChatMessageResponse.
         * Removing or renaming any of these is a breaking API change.
         */
        private val CHAT_MESSAGE_REQUIRED_FIELDS = setOf(
            "id", "roomId", "senderId", "messageType",
            "textContent", "createdAt", "unreadCount"
        )
    }

    @Test
    fun `OpenAPI schema for ChatRoomResponse contains all required fields`() {
        val schemas = fetchOpenApiSchemas()

        val schema = schemas.path("ChatRoomResponse")
        assertTrue(!schema.isMissingNode) {
            "Schema 'ChatRoomResponse' not found in OpenAPI spec — check controller/DTO registration"
        }

        val properties = schema.path("properties").fieldNames().asSequence().toSet()
        val missing = CHAT_ROOM_REQUIRED_FIELDS - properties
        assertTrue(missing.isEmpty()) {
            "ChatRoomResponse OpenAPI schema is missing required field(s): $missing"
        }
    }

    @Test
    fun `OpenAPI schema for ChatMessageResponse contains all required fields`() {
        val schemas = fetchOpenApiSchemas()

        val schema = schemas.path("ChatMessageResponse")
        assertTrue(!schema.isMissingNode) {
            "Schema 'ChatMessageResponse' not found in OpenAPI spec — check controller/DTO registration"
        }

        val properties = schema.path("properties").fieldNames().asSequence().toSet()
        val missing = CHAT_MESSAGE_REQUIRED_FIELDS - properties
        assertTrue(missing.isEmpty()) {
            "ChatMessageResponse OpenAPI schema is missing required field(s): $missing"
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
