// SMS 수신번호 마스킹(SEC-008) 로직을 검증하는 단위 테스트
package com.goldpet.domain.auth.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * SEC-008 — SMS 발송 로그에 수신 전화번호 전체(PII)가 남지 않도록 마스킹한다.
 * 가운데 자리를 가리고 앞 3자리 + 뒤 4자리만 남긴다.
 */
class NCloudSmsSenderMaskTest {

    @Test
    fun `11자리 휴대폰 번호는 가운데를 마스킹한다`() {
        assertEquals("010****5678", NCloudSmsSender.maskPhoneNumber("01012345678"))
    }

    @Test
    fun `10자리 번호도 앞3 뒤4만 남기고 마스킹한다`() {
        assertEquals("021***5678", NCloudSmsSender.maskPhoneNumber("0212345678"))
    }

    @Test
    fun `너무 짧은 번호는 전부 마스킹한다`() {
        assertEquals("*******", NCloudSmsSender.maskPhoneNumber("1234567"))
    }

    @Test
    fun `빈 문자열은 그대로 빈 문자열을 반환한다`() {
        assertEquals("", NCloudSmsSender.maskPhoneNumber(""))
    }
}
