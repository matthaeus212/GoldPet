package com.goldpet.domain.emoticon.controller

import com.goldpet.domain.emoticon.dto.EmoticonResponse
import com.goldpet.domain.emoticon.service.EmoticonService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "이모티콘", description = "이모티콘 목록 조회")
@RestController
@RequestMapping("/api/v1/emoticons")
class EmoticonController(
    private val emoticonService: EmoticonService
) {
    @Operation(summary = "활성 이모티콘 목록 조회")
    @GetMapping
    fun getEmoticons(): ResponseEntity<List<EmoticonResponse>> {
        return ResponseEntity.ok(emoticonService.getActiveEmoticons())
    }
}
