package com.goldpet.domain.common.error

import com.goldpet.domain.auth.exception.AccountStatusException
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.user.entity.UserStatus
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.server.ResponseStatusException

class GlobalExceptionHandlerTest {

    private lateinit var handler: GlobalExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = GlobalExceptionHandler()
    }

    // --- Domain exceptions ---

    @Test
    fun `NotFoundException returns 404 with NOT_FOUND code`() {
        val ex = NotFoundException("Pet not found")
        val response = handler.handleNotFoundException(ex)
        assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode.value())
        assertEquals("NOT_FOUND", response.body?.errorCode)
        assertEquals("Pet not found", response.body?.message)
    }

    @Test
    fun `BadRequestException returns 400 with BAD_REQUEST code`() {
        val ex = BadRequestException("Invalid input")
        val response = handler.handleBadRequestException(ex)
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode.value())
        assertEquals("BAD_REQUEST", response.body?.errorCode)
        assertEquals("Invalid input", response.body?.message)
    }

    @Test
    fun `ForbiddenException returns 403 with FORBIDDEN code`() {
        val ex = ForbiddenException("Not authorized")
        val response = handler.handleForbiddenException(ex)
        assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
        assertEquals("FORBIDDEN", response.body?.errorCode)
        assertEquals("Not authorized", response.body?.message)
    }

    @Test
    fun `ConflictException returns 409 with CONFLICT code`() {
        val ex = ConflictException("Already exists")
        val response = handler.handleConflictException(ex)
        assertEquals(HttpStatus.CONFLICT.value(), response.statusCode.value())
        assertEquals("CONFLICT", response.body?.errorCode)
        assertEquals("Already exists", response.body?.message)
    }

    @Test
    fun `UnauthorizedException returns 401 with UNAUTHORIZED code`() {
        val ex = UnauthorizedException("Invalid credentials")
        val response = handler.handleUnauthorizedException(ex)
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.statusCode.value())
        assertEquals("UNAUTHORIZED", response.body?.errorCode)
        assertEquals("Invalid credentials", response.body?.message)
    }

    // --- Legacy / framework exceptions ---

    @Test
    fun `EntityNotFoundException returns 404 with NOT_FOUND code`() {
        val ex = EntityNotFoundException("Entity not found")
        val response = handler.handleEntityNotFoundException(ex)
        assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode.value())
        assertEquals("NOT_FOUND", response.body?.errorCode)
        assertEquals("Entity not found", response.body?.message)
    }

    @Test
    fun `IllegalArgumentException returns 400 with BAD_REQUEST code`() {
        val ex = IllegalArgumentException("Bad argument")
        val response = handler.handleIllegalArgumentException(ex)
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode.value())
        assertEquals("BAD_REQUEST", response.body?.errorCode)
        assertEquals("Bad argument", response.body?.message)
    }

    @Test
    fun `NoSuchElementException returns 404 with NOT_FOUND code`() {
        val ex = NoSuchElementException("No such element")
        val response = handler.handleNoSuchElementException(ex)
        assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode.value())
        assertEquals("NOT_FOUND", response.body?.errorCode)
        assertEquals("No such element", response.body?.message)
    }

    @Test
    fun `AccountStatusException returns 403 with custom errorCode and status-based message`() {
        val ex = AccountStatusException(UserStatus.DORMANT, "DORMANT_ACCOUNT")
        val response = handler.handleAccountStatusException(ex)
        assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
        assertEquals("DORMANT_ACCOUNT", response.body?.errorCode)
        // AccountStatusException message is "Account is DORMANT"
        assertEquals("Account is DORMANT", response.body?.message)
    }

    @Test
    fun `ResponseStatusException returns matching HTTP status and reason as message`() {
        val ex = ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limited")
        val response = handler.handleResponseStatusException(ex)
        assertEquals(429, response.statusCode.value())
        // handler uses status.name as errorCode
        assertEquals("TOO_MANY_REQUESTS", response.body?.errorCode)
        // handler uses ex.reason as message
        assertEquals("Rate limited", response.body?.message)
    }

    @Test
    fun `ResponseStatusException without reason falls back to status reasonPhrase`() {
        val ex = ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)
        val response = handler.handleResponseStatusException(ex)
        assertEquals(503, response.statusCode.value())
        assertEquals("SERVICE_UNAVAILABLE", response.body?.errorCode)
        assertEquals("Service Unavailable", response.body?.message)
    }

    // --- Validation ---

    @Test
    fun `HttpMessageNotReadableException returns 400 with MALFORMED_REQUEST and fixed message`() {
        val ex = HttpMessageNotReadableException("Bad JSON", MockHttpInputMessage("".toByteArray()))
        val response = handler.handleHttpMessageNotReadableException(ex)
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode.value())
        assertEquals("MALFORMED_REQUEST", response.body?.errorCode)
        // handler returns a fixed Korean message, not the exception message
        assertEquals("요청 본문을 읽을 수 없습니다", response.body?.message)
    }

    // --- Spring Security exceptions ---

    @Test
    fun `AuthenticationException returns 401 with UNAUTHORIZED code`() {
        val ex = BadCredentialsException("Bad credentials")
        val response = handler.handleAuthenticationException(ex)
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.statusCode.value())
        assertEquals("UNAUTHORIZED", response.body?.errorCode)
        assertEquals("Bad credentials", response.body?.message)
    }

    @Test
    fun `AccessDeniedException returns 403 with ACCESS_DENIED code`() {
        val ex = AccessDeniedException("No access")
        val response = handler.handleAccessDeniedException(ex)
        assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
        assertEquals("ACCESS_DENIED", response.body?.errorCode)
        assertEquals("No access", response.body?.message)
    }

    // --- Catch-all ---

    @Test
    fun `Generic Exception returns 500 with INTERNAL_ERROR and fixed message`() {
        val ex = RuntimeException("Something broke")
        val response = handler.handleGenericException(ex)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.statusCode.value())
        assertEquals("INTERNAL_ERROR", response.body?.errorCode)
        // handler returns fixed Korean message
        assertEquals("서버 내부 오류가 발생했습니다", response.body?.message)
    }

    // --- ErrorResponse structure ---

    @Test
    fun `ErrorResponse includes all required fields with correct values`() {
        val ex = NotFoundException("Test resource")
        val response = handler.handleNotFoundException(ex)
        val body = response.body!!
        assertNotNull(body.timestamp)
        assertEquals(404, body.status)
        assertEquals("Not Found", body.error)
        assertEquals("NOT_FOUND", body.errorCode)
        assertEquals("Test resource", body.message)
        assertNull(body.details)
    }

    @Test
    fun `DomainException supports custom errorCode override`() {
        val ex = NotFoundException("Pet image missing", errorCode = "PET_IMAGE_NOT_FOUND")
        val response = handler.handleNotFoundException(ex)
        assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode.value())
        assertEquals("PET_IMAGE_NOT_FOUND", response.body?.errorCode)
        assertEquals("Pet image missing", response.body?.message)
    }
}
