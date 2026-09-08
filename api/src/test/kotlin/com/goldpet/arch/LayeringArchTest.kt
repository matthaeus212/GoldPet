// 계층 규칙 성문화: 컨트롤러(HTTP/WS)는 리포지토리에 직접 의존하지 않는다 (ARCH-002/ARCH-003/ARCH-005)
package com.goldpet.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * ARCH-002: 이 코드베이스에는 계층/의존 규칙을 강제하는 ArchUnit 규칙이 PII 용 하나뿐이었고,
 * 그 결과 컨트롤러가 리포지토리를 직접 주입해 조회·조립·인가를 수행하는 계층 누수가 여러 곳에
 * 쌓였다(ARCH-003 ChatRoomController, ARCH-005 Friend/Like/Map/Auth/DevAuth/UserMigration).
 *
 * W4 에서 그 7개 컨트롤러를 전부 서비스 계약으로 전환했고, 이 테스트는 **되돌아가지 못하게**
 * 고정한다. 새 컨트롤러가 리포지토리를 주입하면 여기서 빨간불이 켜진다.
 *
 * 의도적 실패 검증: 아무 컨트롤러에 `private val userRepository: UserRepository` 를 추가하면
 * 이 테스트가 위반 위치를 찍으며 실패한다.
 */
class LayeringArchTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.goldpet")

    @Test
    fun `controllers must not depend on repositories`() {
        noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because(
                "컨트롤러는 HTTP/WS 경계만 담당한다. 조회·조립·인가 판단은 서비스 계약으로 노출하라 " +
                    "(ARCH-003/ARCH-005). 리포지토리를 직접 쓰면 같은 로직이 REST/WS 두 곳에 중복되고 " +
                    "트랜잭션 경계가 흐려진다."
            )
            .check(classes)
    }

    /**
     * 리포지토리는 스토리지 접근 계약이다. 서비스가 아닌 곳(컨트롤러/설정/인터셉터)에서 쓰이기 시작하면
     * 위 규칙을 우회하는 경로가 생긴다. 인가 인터셉터(StompAuthChannelInterceptor)는 SUBSCRIBE 시점의
     * 참여자 검증이 필요해 예외로 둔다 — 서비스 호출로 바꾸면 매 프레임 트랜잭션이 열린다.
     */
    @Test
    fun `repositories are used only by services and the stomp authz interceptor`() {
        noClasses()
            .that().resideInAPackage("..controller..")
            .or().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("DTO 와 컨트롤러는 저장소를 알지 못해야 한다 (ARCH-002)")
            .check(classes)
    }
}
