package com.goldpet.domain.user.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Size

data class UserProfileUpdateRequest(
    @field:Size(max = 20, message = "이름은 20자 이내여야 합니다")
    val name: String?,
    @field:Size(max = 15, message = "닉네임은 15자 이내여야 합니다")
    val nickname: String?,
    val birthDate: String?,
    val phoneNumber: String?,
    val gender: String?,
    @field:JsonProperty("hasPet")
    val hasPet: Boolean?,
    @field:Size(max = 100, message = "자기소개는 100자 이내여야 합니다")
    val intro: String?,
    val mbti: String?,
    val interests: List<String> = emptyList(),
    val hobbies: List<String> = emptyList(),
    val mainLocationText: String? = null,
    val mainLocationLat: Double? = null,
    val mainLocationLng: Double? = null
)
