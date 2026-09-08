package com.goldpet.controller

import com.goldpet.config.openapi.OpenApiInternal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@OpenApiInternal
@RestController
class RootController {

    @GetMapping("/")
    fun root(): Map<String, String> {
        return mapOf(
            "status" to "up",
            "message" to "GoldPet API is running",
            "version" to "v1"
        )
    }
}
