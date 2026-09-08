package com.goldpet.domain.friend.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.friend.dto.BlockRequest
import com.goldpet.domain.friend.dto.BlockedUserResponse
import com.goldpet.domain.friend.dto.LikeRequest
import com.goldpet.domain.friend.dto.MatchResponse
import com.goldpet.domain.friend.service.MatchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Match", description = "매칭/친구 관련 API")
@RestController
@RequestMapping("/api/v1/matches")
class MatchController(
    private val matchService: MatchService
) {
    @Operation(summary = "사용자 좋아요", description = "다른 사용자를 좋아요합니다. 상호 좋아요 시 매칭이 생성됩니다.")
    @PostMapping("/like")
    fun likeUser(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: LikeRequest
    ): ResponseEntity<Map<String, Any>> {
        val isMatchCreated = matchService.likeUser(principal.id, request.toUserId, request.source)
        val response = mapOf(
            "isMatch" to isMatchCreated,
            "message" to if (isMatchCreated) "매칭되었습니다!" else "좋아요를 보냈습니다."
        )
        return if (isMatchCreated) {
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }

    @Operation(summary = "내 매칭 목록", description = "나와 매칭된 사용자 목록을 조회합니다.")
    @GetMapping
    fun getMyMatches(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<List<MatchResponse>> {
        return ResponseEntity.ok(matchService.getMyMatches(principal.id))
    }

    @Operation(summary = "매칭 상세", description = "특정 매칭의 상세 정보를 조회합니다.")
    @GetMapping("/{matchId}")
    fun getMatchDetail(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable matchId: Long
    ): ResponseEntity<MatchResponse> {
        return ResponseEntity.ok(matchService.getMatchDetail(matchId, principal.id))
    }

    @Operation(summary = "매칭 취소", description = "매칭을 취소합니다.")
    @DeleteMapping("/{matchId}")
    fun cancelMatch(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable matchId: Long
    ): ResponseEntity<Void> {
        matchService.cancelMatch(matchId, principal.id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "사용자 차단", description = "다른 사용자를 차단합니다.")
    @PostMapping("/block")
    fun blockUser(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: BlockRequest
    ): ResponseEntity<Void> {
        matchService.blockUser(principal.id, request.blockedUserId)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "사용자 차단 해제", description = "차단을 해제합니다.")
    @DeleteMapping("/block/{blockedUserId}")
    fun unblockUser(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable blockedUserId: Long
    ): ResponseEntity<Void> {
        matchService.unblockUser(principal.id, blockedUserId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "차단 목록", description = "내가 차단한 사용자 목록을 조회합니다.")
    @GetMapping("/blocks")
    fun getBlockList(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<List<BlockedUserResponse>> {
        return ResponseEntity.ok(matchService.getBlockList(principal.id))
    }
}
