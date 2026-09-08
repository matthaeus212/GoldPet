package com.goldpet.domain.common.exception

/**
 * Base domain exception. Does NOT carry httpStatus (transport concern stays in handler).
 * errorCode defaults to a machine-readable code derived from the exception type.
 */
abstract class DomainException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class NotFoundException(
    message: String,
    errorCode: String = "NOT_FOUND",
    cause: Throwable? = null
) : DomainException(errorCode, message, cause)

class BadRequestException(
    message: String,
    errorCode: String = "BAD_REQUEST",
    cause: Throwable? = null
) : DomainException(errorCode, message, cause)

class ForbiddenException(
    message: String,
    errorCode: String = "FORBIDDEN",
    cause: Throwable? = null
) : DomainException(errorCode, message, cause)

class ConflictException(
    message: String,
    errorCode: String = "CONFLICT",
    cause: Throwable? = null
) : DomainException(errorCode, message, cause)

class UnauthorizedException(
    message: String,
    errorCode: String = "UNAUTHORIZED",
    cause: Throwable? = null
) : DomainException(errorCode, message, cause)
