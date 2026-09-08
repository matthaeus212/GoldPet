# SEC-001 — Naver 클라이언트 시크릿 외부화 계획

> 출처: 2026-06-19 종합 코드 리뷰(`code-review-report.md`) SEC-001. 외부 리뷰(codex)·내부 보안 감사 공동 확인.
> 상태: **계획 수립 완료 / 적용 대기**. 아래 "적용 타이밍" 참조.

## 1. 배경과 제약 (의사결정 기록)
- **회전(rotation) 안 함:** git 레포가 **비공개**이므로 노출된 시크릿을 무효화(Naver 콘솔 재발급)하지 않기로 결정(사용자 판단, 2026-06-19). → 본 작업은 *회전이 아니라 외부화(향후 커밋에서 시크릿 분리)* 만 수행한다.
- **지금 배포 불가:** iOS·Android 양쪽 **App Store / Play 심사 진행 중**(빌드 4). 빌드 설정 변경이 어긋나면 제출/차기 바이너리에서 Naver 로그인이 깨질 수 있다. → **심사 통과 후 차기 빌드(build 5+)** 에 적용.
- **계획 우선:** 코드 변경은 본 문서로 먼저 합의하고, 적용은 별도 작업으로 진행.

## 2. 현황 (정확한 위치)
시크릿이 **양 플랫폼 + git tracked**:

| 플랫폼 | 파일 | 키 | 소비 방식 |
|--------|------|----|----------|
| iOS | `app/ios/Runner/Info.plist:74-75` | `NidClientSecret` (그리고 72 `NidClientID`) | `flutter_naver_login`이 Info.plist에서 네이티브로 읽음 |
| Android | `app/android/app/src/main/res/values/strings.xml:3-4` | `naver_client_secret`, `naver_client_id` | `AndroidManifest.xml:57-58`이 `@string/naver_client_*` meta-data로 SDK에 전달 |

- 두 파일 모두 `git ls-files`에 존재 → **시크릿이 git 히스토리에 잔존**(외부화해도 과거 커밋엔 남음. 회전 안 하므로 이는 수용된 리스크).
- **기존 자산:** Android `build.gradle.kts`에 이미 `key.properties`(gitignore) + `java.util.Properties` 로드로 keystore 시크릿을 주입하는 **검증된 패턴**이 있다(`build.gradle.kts:12-15, 42-46`). Naver 시크릿도 같은 패턴을 미러링 → 저위험.
- iOS는 `Flutter/Generated.xcconfig`가 이미 gitignore됨(`app/ios/.gitignore:21`). 동일 디렉토리에 secrets xcconfig를 두고 include.

## 3. 목표 / 비목표
**목표**
- 커밋되는 소스(Info.plist, strings.xml)에서 Naver 시크릿 **리터럴 제거** → 빌드 시 gitignore된 로컬 파일에서 주입.
- 신규 개발자/CI를 위한 `*.example` 템플릿 + 문서화.

**비목표**
- 시크릿 회전(비공개 레포 결정). git 히스토리 purge도 하지 않음(회전 안 하면 무의미).
- 서버측 OAuth code 교환으로의 아키텍처 전환(별도 큰 작업 — 본 계획 범위 외).

## 4. iOS 외부화 절차
1. **`app/ios/Flutter/Secrets.xcconfig`** 생성 (gitignore):
   ```
   NID_CLIENT_ID = <client-id>
   NID_CLIENT_SECRET = <client-secret>
   ```
2. **Debug/Release.xcconfig**에 include 추가(가장 위, Generated 앞):
   ```
   #include? "Secrets.xcconfig"
   ```
   (`#include?` = 파일 없으면 무시 — CI 초기 셋업 안전)
3. **`Info.plist`** 리터럴 → 변수 참조:
   ```xml
   <key>NidClientID</key>
   <string>$(NID_CLIENT_ID)</string>
   <key>NidClientSecret</key>
   <string>$(NID_CLIENT_SECRET)</string>
   ```
   (Info.plist는 빌드 시 `$(VAR)` 치환을 지원 — `FLUTTER_BUILD_NAME` 등 기존 사용과 동일 메커니즘)
4. **`app/ios/Flutter/Secrets.xcconfig.example`** 커밋(빈 값 템플릿).
5. **`.gitignore`** 추가: `app/ios/Flutter/Secrets.xcconfig`
6. ⚠️ **xcconfig 함정:** 값에 `//`(주석으로 해석)나 `$`가 있으면 깨진다. 실제 시크릿에 해당 문자가 있는지 확인 후, 있으면 xcconfig 대신 `.env` + run-script 주입으로 전환.

## 5. Android 외부화 절차 (기존 key.properties 패턴 미러)
1. **`app/android/secrets.properties`** 생성 (gitignore):
   ```
   naverClientId=<client-id>
   naverClientSecret=<client-secret>
   ```
   (또는 기존 `key.properties`에 두 키를 추가해 파일 수를 줄여도 됨 — 일관성 위해 별도 `secrets.properties` 권장)
2. **`build.gradle.kts`** 에 로드 + manifestPlaceholders 주입(keystore 로드 패턴과 동일):
   ```kotlin
   val secretsFile = rootProject.file("secrets.properties")
   val secrets = Properties().apply { if (secretsFile.exists()) load(FileInputStream(secretsFile)) }
   // android { defaultConfig { ... } } 안에:
   manifestPlaceholders["naverClientId"] = (secrets["naverClientId"] ?: "") as String
   manifestPlaceholders["naverClientSecret"] = (secrets["naverClientSecret"] ?: "") as String
   ```
3. **`AndroidManifest.xml:57-58`** → placeholder 참조:
   ```xml
   <meta-data android:name="com.naver.sdk.clientId" android:value="${naverClientId}" />
   <meta-data android:name="com.naver.sdk.clientSecret" android:value="${naverClientSecret}" />
   ```
4. **`strings.xml:3-4`** 에서 `naver_client_id` / `naver_client_secret` **제거**(`naver_client_name`=GoldPet은 비밀 아니므로 유지 가능).
5. **`app/android/secrets.properties.example`** 커밋(빈 값 템플릿).
6. **`.gitignore`** 추가: `app/android/secrets.properties`

> manifestPlaceholder 방식을 택한 이유: 매니페스트가 이미 meta-data로 값을 받으므로 placeholder 치환이 자연스럽고, `resValue`처럼 strings.xml과 리소스명 충돌 위험이 없다.

## 6. 검증 (적용 시 필수)
1. `cd app && flutter build appbundle --build-number=N` (Android) / `flutter build ipa` (iOS) — **빌드 성공** 확인.
2. 빌드 산출물에서 주입 확인:
   - iOS: 빌드된 `Info.plist`에 `$(...)`가 **치환된 실제 값**인지(빈 문자열이면 주입 실패).
   - Android: `./gradlew :app:processReleaseManifest` 후 merged manifest의 meta-data 값 확인.
3. **실기기 Naver 로그인 E2E** — 로그인 성공해야 함(시크릿 주입 실패 시 여기서 실패). auth 변경이므로 성공+실패 케이스 모두(AGENTS.md 규약).
4. `git status` — `Secrets.xcconfig` / `secrets.properties`가 **추적되지 않음** 확인.

## 7. 롤백
- 변경 전 `Info.plist` / `strings.xml` / `AndroidManifest.xml` / `build.gradle.kts` 원복(단일 커밋이므로 revert 1회).
- 로컬 시크릿 파일은 그대로 두면 됨(추적 안 됨).

## 8. 적용 타이밍
- **선행조건:** iOS·Android 현재 심사 빌드(4)가 **승인/배포 완료**될 것.
- 그 다음 차기 빌드(build 5+) 작업 브랜치에서 위 4·5를 **단일 PR**로 적용 → §6 검증 → 머지.
- 심사 중인 빌드의 바이너리/설정은 **건드리지 않는다**.

## 9. 잔여 리스크 (정직)
- 시크릿이 **git 히스토리에 잔존**한다(회전 안 함). 레포 비공개 전제로 수용. 만약 레포가 공개로 전환되거나 유출되면 **즉시 Naver 콘솔 시크릿 회전 필수**.
- 클라이언트(IPA/APK) 바이너리에는 여전히 시크릿이 임베드된다(외부화는 *소스 분리*이지 *클라이언트 은닉*이 아님). 근본 해결은 서버측 OAuth code 교환(비목표).

## 10. 체크리스트 (적용 시)
- [ ] iOS: Secrets.xcconfig 생성 + include + Info.plist `$()` 치환 + .example + .gitignore
- [ ] iOS: 값에 `//`/`$` 없는지 확인
- [ ] Android: secrets.properties + build.gradle.kts 로드/placeholder + Manifest `${}` + strings.xml 제거 + .example + .gitignore
- [ ] 빌드 성공(양 플랫폼) + merged manifest/Info.plist 값 주입 확인
- [ ] 실기기 Naver 로그인 E2E PASS
- [ ] secrets 파일 untracked 확인
- [ ] 차기 빌드(build 5+)에 포함, 심사 빌드는 미변경
