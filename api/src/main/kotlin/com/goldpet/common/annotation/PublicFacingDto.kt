package com.goldpet.common.annotation

/**
 * Marker annotation for DTOs that are serialized to public-facing (cross-user)
 * HTTP endpoints — i.e. responses that one viewer sees about **another** user.
 *
 * Classes annotated with `@PublicFacingDto` are validated by
 * `PublicFacingDtoArchTest` which forbids references to known PII fields on
 * `User` / `UserAuthProvider`. When you need to expose additional user data
 * across the trust boundary, extend the allow-list in that ArchUnit rule —
 * never by silently adding a new field here.
 *
 * See `.omc/plans/community-author-profile-gallery.md` §4-4 for the contract
 * automation rationale.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class PublicFacingDto
