package com.goldpet.arch

import com.goldpet.common.annotation.PublicFacingDto
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.jupiter.api.Test

/**
 * Contract test: `@PublicFacingDto`-annotated classes (and their nested/companion
 * classes) MUST NOT read PII fields from `User` / `UserAuthProvider`.
 *
 * See `.omc/plans/community-author-profile-gallery.md` §4-4.
 *
 * Intentional-failure verification: add `val phone: String?` mapped from
 * `user.phoneNumber` to any `@PublicFacingDto` class → this test fails with a
 * message citing the source location.
 */
class PublicFacingDtoArchTest {

    private val userClassName = "com.goldpet.domain.user.entity.User"
    private val userAuthProviderClassName = "com.goldpet.domain.auth.entity.UserAuthProvider"

    /**
     * Forbidden field names on `User`. Kept as a set of actual Kotlin property
     * names (not plan aliases) so the rule binds to real JVM fields that exist
     * on the entity today.
     *
     * Plan aliases → actual fields:
     *   - `realName`      → `name`
     *   - `phoneNumberHash` → (n/a — User only has encrypted `phoneNumber`)
     *   - `regionLatLng`  → `mainLocationGeom` (and `mainLocationText` kept PII-adjacent)
     */
    private val forbiddenUserFields = setOf(
        "phoneNumber",
        "email",
        "emailHash",
        "birthDate",
        "birthYear",
        "gender",
        "name",
        "password",
        "fcmToken",
        "mainLocationGeom",
        "mainLocationText",
        "profileLockedAt",
        "oauthProvider",
        "oauthId",
    )

    @Test
    fun `public-facing DTOs do not reference User or UserAuthProvider PII fields`() {
        val importedClasses = ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.goldpet")

        val rule = classes()
            .that(isAnnotatedOrEnclosedBy(PublicFacingDto::class.java))
            .should(notAccessUserPiiFields())
            .because(
                "PublicFacingDto 의 필드/매핑 로직은 User·UserAuthProvider 의 PII 필드를 읽으면 안 됩니다. " +
                    "신규 필드 추가 시 plan §4-4 의 allow-list 갱신 → 리뷰 필수.",
            )

        rule.check(importedClasses)
    }

    private fun isAnnotatedOrEnclosedBy(
        annotation: Class<out Annotation>,
    ): com.tngtech.archunit.base.DescribedPredicate<JavaClass> {
        return object : com.tngtech.archunit.base.DescribedPredicate<JavaClass>(
            "annotated with or enclosed by @${annotation.simpleName}",
        ) {
            override fun test(input: JavaClass): Boolean {
                var current: JavaClass? = input
                while (current != null) {
                    if (current.isAnnotatedWith(annotation)) return true
                    current = current.enclosingClass.orElse(null)
                }
                return false
            }
        }
    }

    private fun notAccessUserPiiFields(): ArchCondition<JavaClass> {
        return object : ArchCondition<JavaClass>(
            "not read PII fields on User/UserAuthProvider",
        ) {
            override fun check(javaClass: JavaClass, events: ConditionEvents) {
                for (access in javaClass.fieldAccessesFromSelf) {
                    val target = access.target
                    val owner = target.owner.fullName
                    val fieldName = target.name
                    val forbidden = when (owner) {
                        userClassName -> fieldName in forbiddenUserFields
                        userAuthProviderClassName -> true
                        else -> false
                    }
                    if (forbidden) {
                        events.add(
                            SimpleConditionEvent.violated(
                                access,
                                "${javaClass.fullName} reads PII field $owner#$fieldName at ${access.sourceCodeLocation}",
                            ),
                        )
                    }
                }
            }
        }
    }
}
