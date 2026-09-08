package com.goldpet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.util.TimeZone

@EnableJpaAuditing
@EnableCaching
@SpringBootApplication
class GoldPetApplication

fun main(args: Array<String>) {
    // 모든 LocalDateTime.now() 호출이 KST를 사용하도록 JVM 타임존 고정
    // (서버 OS가 UTC일 경우 admin datetime-local 입력값과 불일치 방지)
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    runApplication<GoldPetApplication>(*args)
}
