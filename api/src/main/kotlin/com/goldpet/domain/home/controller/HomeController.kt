package com.goldpet.domain.home.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.home.dto.HomeResponse
import com.goldpet.domain.home.service.HomeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Home", description = "홈 화면 데이터 API")
@RestController
@RequestMapping("/api/v1/home")
class HomeController(
    private val homeService: HomeService
) {
    @Operation(summary = "홈 화면 데이터 조회")
    @GetMapping
    fun getHomeData(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<HomeResponse> {
        val homeData = homeService.getHomeData(principal.id)
        return ResponseEntity.ok(homeData)
    }
}
