package com.goldpet.domain.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.IntegrationTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@Tag("integration")
class AuthControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    // --- checkUsername ---

    @Test
    fun `checkUsername should return 200 for valid username`() {
        mockMvc.perform(
            post("/api/v1/auth/check-username")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("username" to "validuser")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").isBoolean)
    }

    @Test
    fun `checkUsername should return 400 for blank username`() {
        mockMvc.perform(
            post("/api/v1/auth/check-username")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("username" to "")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
    }

    // --- checkNickname ---

    @Test
    fun `checkNickname should return 200 for valid nickname`() {
        mockMvc.perform(
            post("/api/v1/auth/check-nickname")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("nickname" to "멋진반려인")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").isBoolean)
    }

    @Test
    fun `checkNickname should return 400 for blank nickname`() {
        mockMvc.perform(
            post("/api/v1/auth/check-nickname")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("nickname" to "")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
    }

    // --- refreshToken ---

    @Test
    fun `refreshToken should return 400 for blank refreshToken`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("refreshToken" to "")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
    }

    @Test
    fun `refreshToken should return 401 for invalid token`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("refreshToken" to "this.is.not.a.valid.jwt")))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
    }

    // --- nativeLogin ---

    @Test
    fun `nativeLogin should return 400 for blank accessToken`() {
        mockMvc.perform(
            post("/api/v1/auth/login/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("accessToken" to "")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
    }

    @Test
    fun `nativeLogin should return 400 for unsupported provider`() {
        mockMvc.perform(
            post("/api/v1/auth/login/twitter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("accessToken" to "some-access-token")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
    }
}
