package com.goldpet.config.openapi

/**
 * Marks a controller as intentionally excluded from the OpenAPI annotation coverage check.
 * Use ONLY for internal/infrastructure controllers that do not belong in the public API surface:
 *   - GlobalExceptionHandler (not a controller — @RestControllerAdvice-adjacent)
 *   - RootController (empty redirect)
 *   - CloudflareController (health probes)
 *
 * Adding this annotation requires code-reviewer approval — default assumption is that every
 * controller belongs in the public spec.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class OpenApiInternal
