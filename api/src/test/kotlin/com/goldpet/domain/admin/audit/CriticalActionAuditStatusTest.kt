package com.goldpet.domain.admin.audit

import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.ConflictException
import com.goldpet.domain.common.exception.ForbiddenException
import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.common.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * CriticalActionAuditAspect.resolveStatus 가 GlobalExceptionHandler 매핑과 일치하는지 검증.
 * (audit response_status 정확도 — 하드코딩 500 회귀 방지)
 */
class CriticalActionAuditStatusTest {

    @Test
    fun `domain exceptions map to the same status as GlobalExceptionHandler`() {
        val r = CriticalActionAuditAspect::resolveStatus
        assertThat(r(BadRequestException("bad"))).isEqualTo(400)
        assertThat(r(IllegalArgumentException("bad"))).isEqualTo(400)
        assertThat(r(UnauthorizedException("no"))).isEqualTo(401)
        assertThat(r(ForbiddenException("no"))).isEqualTo(403)
        assertThat(r(NotFoundException("missing"))).isEqualTo(404)
        assertThat(r(ConflictException("dup"))).isEqualTo(409)
        assertThat(r(OptimisticLockingFailureException("race"))).isEqualTo(409)
    }

    @Test
    fun `ResponseStatusException uses its own status`() {
        assertThat(CriticalActionAuditAspect.resolveStatus(ResponseStatusException(HttpStatus.NOT_FOUND)))
            .isEqualTo(404)
        assertThat(CriticalActionAuditAspect.resolveStatus(ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)))
            .isEqualTo(429)
    }

    @Test
    fun `unmapped exceptions fall back to 500`() {
        assertThat(CriticalActionAuditAspect.resolveStatus(RuntimeException("boom"))).isEqualTo(500)
        assertThat(CriticalActionAuditAspect.resolveStatus(NullPointerException())).isEqualTo(500)
    }
}
