# 골드펫 상용 출시 — 회사 계정 전환 플랜

**작성일**: 2026-05-19
**대상 법인**: 만남스퀘어(주)
**회사 도메인**: mannamsquare.com
**Bundle ID**: `com.mannam.goldpet` (변경 금지)
**Widget Bundle ID**: `com.mannam.goldpet.WalkActivityWidget`
**현재 상태(2026-06-22 갱신)**: 애플 — 개인 Team `D99344V928`에 앱 **심사 통과·수동 배포 대기(미배포)** / 구글 — **ciccosoft inc** 계정에 업로드·**심사 중**(Play 설치 0건)
**목표**: 7월 초 상용 출시

---

> ## ⚠️ 정정 안내 (2026-06-22)
> 본 문서는 2026-05-19 작성본으로, 이후 실제 상황이 달라졌고 일부 사실이 틀렸습니다. **최신 의사결정은 [GoldPet_스토어계정_법인전환_정리_260622.md](./GoldPet_스토어계정_법인전환_정리_260622.md)를 따르세요.** 주요 정정:
> - **구글 14일 룰은 개인 계정 전용 — 법인(Organization)은 면제** (본문 §1-1, §4-1, §함정3의 "신규 법인 14일 의무"는 오류). 아래 해당 부분에 인라인 정정 표시.
> - **애플은 신규 Organization 가입이 아니라 개인→법인 "전환"** (현재 개인 계정 보유 → 같은 Team ID·앱 유지, 새 앱 생성 불필요).
> - **구글 앱이 이미 ciccosoft에 올라가 있음** → "미등록" 아님. MANNAM으로 가려면 **앱 이전 또는 0설치 중 재업로드** 필요(패키지명 잠금 주의).

---

## 핵심 원칙

1. **Bundle ID는 절대 바꾸지 않는다** (`com.mannam.goldpet` 그대로)
2. **소유 계정과 키/인증서만 회사 명의로 교체**한다
3. **회사 도메인 메일(@mannamsquare.com)로 모든 외부 계정을 만든다**
4. **개인 계정과 회사 계정을 깔끔히 분리한다** (인수인계/감사 대응 대비)

---

## 큰 그림 (3주~1.5개월 소요)

```
[Phase 1] 회사 명의 계정 개설         (1~2주, 외부 승인 대기)
   ↓
[Phase 2] 백엔드/3rd-party 키 재발급  (Phase 1 진행 중 병행)
   ↓
[Phase 3] 앱 빌드 설정 교체           (코드/설정 작업)
   ↓
[Phase 4] 스토어 등록 + 심사 제출
   ↓
[Phase 5] 출시 후 개인 계정 정리
```

---

## Phase 1. 회사 명의 개발자 계정 개설 (가장 먼저, 병목)

### 1-1. Google Play Console (Organization) — **최우선**
- URL: https://play.google.com/console/signup
- 계정 유형: **조직(Organization)**
- 가입 이메일: `admin@mannamsquare.com` 또는 `dev@mannamsquare.com` (개인 Gmail 금지)
- 필수: D-U-N-S 번호 (보유), 사업자등록증, 법인카드
- 비용: 일회성 $25
- 소요: 보통 1~3일
- ~~⚠️ 신규 법인 계정은 클로즈드 테스트 14일 + 테스터 12명 이상 의무 (2024.11~)~~ → **[정정] 14일·12명 룰은 개인(personal) 계정 전용. 법인(Organization)은 면제** ([출처](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)). 계정 개설은 여전히 병목이니 먼저 시작.

### 1-2. Apple Developer Program — **개인→법인 전환** (신규 가입 아님) [정정]
- 현재 **개인(Individual) 계정 보유** (Team ID `D99344V928`, 앱 심사 통과·미배포) → **신규 Organization 가입이 아니라 "전환(conversion)"** 으로 진행
- 전환 시 **Apple ID·Team ID·인증서·기존 앱 모두 유지, 판매자 이름만 변경** → 새 앱 생성/마이그레이션 불필요
- 방법: Apple Developer Support에 전환 요청 (셀프 신규가입 아님)
- 필수: D-U-N-S 번호 (보유), 법인 주소(서울 강남 — 제주 개인주소 아님), 대표 전화번호, 공개 웹사이트(mannamsquare.com), 서명 권한
- 소요: ~2주 (Apple이 대표 전화번호로 검증 통화)
- 상세: [GoldPet_스토어계정_법인전환_정리_260622.md §1](./GoldPet_스토어계정_법인전환_정리_260622.md)

### 1-3. 회사 도메인 메일 계정 정리
출시 전에 회사 도메인 메일을 분리해서 생성:
- `apple-dev@mannamsquare.com` (Apple Connect 전용)
- `google-play@mannamsquare.com` (Play Console 전용)
- `firebase@mannamsquare.com` (Firebase/GCP 전용)

각각 2FA 활성화 + 백업 코드 1Password Business 등에 보관.

### 1-4. App Store Connect / Play Console 사용자 권한
- 본인(`jioabang@gmail.com`) 계정을 회사 계정의 **Admin/Developer 역할**로 초대받기
- 법인 대표 메일은 Account Holder, 본인은 Admin

---

## Phase 2. 3rd-party 서비스 재발급 (Phase 1 대기 중 병행)

전부 회사 계정/회사 이메일로 다시 만들어야 합니다.

| 우선순위 | 서비스 | URL | 산출물 |
|---|---|---|---|
| 1 | **Firebase 새 프로젝트** | console.firebase.google.com | `goldpet-prod` 프로젝트, Android/iOS 앱 등록 (`com.mannam.goldpet`), `google-services.json`, `GoogleService-Info.plist`, `firebase-service-account.json` |
| 2 | **Apple Push (APNs)** | Apple Developer Portal | APNs Key (.p8) → Firebase Console에 업로드 + Key ID + Team ID |
| 3 | **AWS prod 계정** | aws.amazon.com | S3 버킷 `goldpet-prod`, IAM 사용자 (Access Key/Secret) |
| 4 | **Kakao Developers** ⚠️ | developers.kakao.com | 새 앱, **비즈앱 심사 신청** (1~2주 소요!), Native App Key |
| 5 | **Naver Developers (로그인)** | developers.naver.com | 네이버 로그인 Client ID/Secret |
| 6 | **Naver Cloud Maps** | console.ncloud.com | Maps API Client ID (Android/iOS 패키지 등록) |
| 7 | **Google OAuth** | Firebase 프로젝트 안에서 자동 | Web/Android/iOS Client ID (SHA-1/SHA-256 등록 필요) |
| 8 | **Apple Sign-in** | Apple Developer Portal | App ID에서 활성화 + Service ID + Key (.p8) |

### Kakao 비즈앱 심사 — 가장 위험한 일정
- 전화번호/이메일 동의 항목을 받으려면 비즈앱 필수
- 심사 1~2주 소요
- **이번 주 안에 신청해야 출시 일정 안 밀림**

### 의사결정: Kakao 사용자 ID 변경
TestFlight 사용자 8명만 영향 → 새 Kakao 앱 생성 후 "재로그인 필요" 안내 1회로 정리.
기존 개인 Kakao 앱 administrator 이전 절차는 생략.

---

## Phase 3. 앱 빌드 설정 교체

### 3-1. release keystore 생성 (Android, Phase 2 중)
```bash
keytool -genkey -v -keystore goldpet-release.jks -alias goldpet \
  -keyalg RSA -keysize 2048 -validity 10000
keytool -list -v -keystore goldpet-release.jks -alias goldpet
# → SHA-1, SHA-256 추출
```

**SHA-1, SHA-256을 다음 콘솔에 모두 등록**:
- Firebase Console (Android 앱)
- Kakao Developers (Android 플랫폼)
- Naver Developers
- Google OAuth Client (Android)

**keystore는 회사 비밀번호 관리자(1Password Business 등)에 저장.** 분실 시 앱 업데이트 영구 불가.

권장: **Play App Signing 활성화** — Google이 production 서명 키 보관, 본인은 upload 키만 관리.

### 3-2. iOS (`app/ios/`)
- [ ] Apple Developer Portal에서 회사 Team으로 App ID 새로 등록:
  - `com.mannam.goldpet`
  - `com.mannam.goldpet.WalkActivityWidget`
- [ ] Capabilities 활성화 (Push, Sign in with Apple, App Groups for Live Activity)
- [ ] Distribution Certificate + Provisioning Profile 새 발급
- [ ] `Runner.xcodeproj/project.pbxproj`의 `DEVELOPMENT_TEAM = D99344V928;` → **회사 Team ID로 전부 교체**
  - Runner, RunnerTests, WalkActivityWidget 모두
- [ ] `ios/Runner/GoogleService-Info.plist` 교체 (회사 Firebase에서 다운로드)
- [ ] `ios/Runner/Info.plist`의 URL Schemes (Kakao/Naver/Google appID) 새 키로 교체
- [ ] `ios/fastlane/Appfile`의 `team_id`, `apple_id` 교체
- [ ] APNs Key(.p8) Firebase Console에 업로드

### 3-3. Android (`app/android/`)
- [ ] `app/google-services.json` 교체
- [ ] `key.properties` 새 keystore 경로/비번 설정
- [ ] `strings.xml` 또는 BuildConfig의 Kakao native app key 교체
- [ ] `AndroidManifest.xml`의 `kakao{NEW_APP_KEY}` redirect scheme 갱신
- [ ] Naver Maps client ID 교체

### 3-4. Backend (`api/`)
- [ ] `application-prod.yml`: Kakao/Naver/Google/Apple OAuth client id+secret 새 값
- [ ] `application-prod.yml`: AWS S3 prod 자격증명 (Access Key/Secret/Region/Bucket)
- [ ] `firebase-service-account.json` 새 파일 prod 배포 (Jenkins 시크릿)
- [ ] Apple Sign-in: 새 Service ID + Team ID + Key ID 반영
- [ ] JWT 비밀키는 변경하지 않음 (개인/회사 무관, 변경하면 기존 토큰 전부 만료)

### 3-5. Frontend/Admin
- [ ] `frontend/.env.prod`:
  - `VITE_KAKAO_JS_KEY`
  - `VITE_NAVER_CLIENT_ID`
  - `VITE_GOOGLE_CLIENT_ID`
  - `VITE_NAVER_MAPS_CLIENT_ID`
- [ ] `admin/.env.prod`: 동일

### 3-6. 검증 (배포 전 필수)
- [ ] 로컬에서 prod 빌드 한 번 실행:
  - `flutter build apk --release --dart-define=ENV=prod`
  - `flutter build ipa --dart-define=ENV=prod`
- [ ] dev 환경 빌드 파이프라인은 건드리지 말 것 (개인 계정으로 dev 계속 사용 가능)

---

## Phase 4. 스토어 등록 + 심사

### 4-1. Google Play
> **[정정]** 아래 1~3의 14일·12명 클로즈드 테스트는 **개인 계정 전용이며 법인(Organization) 계정은 면제**. MANNAM 법인 계정으로 진행하면 곧바로 프로덕션 트랙 제출 가능(아래 4부터). 또한 앱이 이미 **ciccosoft**에 올라가 있어, MANNAM으로 가려면 **앱 이전 또는 0설치 중 재업로드**가 선행되어야 함 → [정리 문서 §2](./GoldPet_스토어계정_법인전환_정리_260622.md) 참조.

1. ~~AAB(Android App Bundle) 업로드 → **클로즈드 테스트 트랙**~~ (법인 면제)
2. ~~테스터 12명 이상 모집~~ (법인 면제)
3. ~~**14일간 옵트인 + 실사용 의무**~~ (법인 면제)
4. 프로덕션 트랙 제출 → 심사 1~7일
5. 필수 작성:
   - 데이터 보안 양식
   - 콘텐츠 등급
   - 타겟 사용자 (만 18세 이상 권장)
   - 광고 포함 여부

### 4-2. App Store
> **[정정]** 개인→법인 **전환** 방식이면 **기존 앱·TestFlight·인증서가 그대로 유지**됨 → 새 앱 생성/테스터 재초대 **불필요**. 아래 1~2는 "새 계정으로 갈아타는" 옛 가정이라 해당 없음.

1. ~~App Store Connect에 새 앱 생성~~ → 전환 시 기존 앱 유지(현재 심사 통과·미배포 상태 그대로)
2. ~~기존 8명 테스터 재초대~~ → 전환 시 TestFlight 유지로 불필요
3. **App Privacy 양식** 매우 꼼꼼하게 작성:
   - 위치(정밀)
   - 사진 (Photos)
   - 카메라
   - 연락처 (전화번호)
   - 사용자 ID
   - 사용 데이터
   - 진단
4. `Info.plist`의 사용 사유 문구 검토:
   - `NSLocationWhenInUseUsageDescription`: "산책 경로 기록"처럼 **구체적 목적** 명시
   - `NSCameraUsageDescription`
   - `NSPhotoLibraryUsageDescription`
   - `NSMicrophoneUsageDescription` (사용 시)
5. 심사 제출 → 24~48시간

### 4-3. 출시 직전 체크
- [ ] 백엔드 prod 서버 키 전부 회사 명의로 교체 완료 확인
- [ ] Kakao 비즈앱 심사 통과 확인
- [ ] TestFlight 8명에게 "새 빌드로 재로그인 필요" 공지 메일
- [ ] 강제 업데이트 시스템(`app_versions` 테이블) 최소 버전 prod에 사전 입력
  - dev 빌드 사용자가 prod에 못 붙도록 차단

---

## Phase 5. 출시 후 정리

- [ ] 출시 후 안정화(2~4주) 확인 후 **개인 Apple Developer 갱신 중지** ($99/년 절약)
- [ ] 개인 Firebase 프로젝트 (사용자 0명 확인 후) 삭제
- [ ] 개인 Kakao/Naver 앱 비활성화
- [ ] 회사 계정 **2FA 백업 코드** 회사 보안 담당자와 공유
- [ ] **Jenkins prod 빌드 환경변수** 전체 회사 키로 교체 완료 확인

---

## ⚠️ 흔한 함정 4가지

### 1. Bundle ID 변경 유혹 금지
"회사 명의니까 `com.mannamsquare.goldpet`로 바꿀까?" → **안 됨**
- DB에 박혀있음
- Deep link 깨짐
- TestFlight 사용자 추적 끊김
- 푸시 토큰 재발급 필요

`com.mannam.goldpet` 그대로 유지.

### 2. 개인 Gmail로 회사 계정 만들기
Apple/Google 둘 다 회사 도메인 메일 강력 권장. 본인 퇴사/이직 시 인수인계가 헬.
→ 반드시 `@mannamsquare.com` 메일 사용.

### 3. ~~Google Play Console 14일 룰 무시~~ [정정: 법인 면제]
~~신규 법인은 무조건 적용.~~ → **14일·12명 룰은 개인 계정 전용, 법인(Organization)은 면제.** 법인 계정으로 출시하면 이 2주 지연 없음. (개인 계정으로 낼 경우에만 해당)

### 4. Kakao 비즈앱 심사 안 거치고 동의항목 사용
전화번호/이메일 동의 받으려면 비즈앱 필수. 출시 직전 발견 시 1~2주 또 밀림.
→ **Week 1 안에 신청**.

---

## D-Day 캘린더

| 주차 | 핵심 액션 |
|---|---|
| **Week 0 (오늘~내일)** | Google Play Console 가입, Apple Developer 가입, 회사 도메인 메일 분리 |
| **Week 1** | 외부 계정 승인 대기 중: Firebase 프로젝트, Kakao 비즈앱 신청, AWS prod, Naver 로그인/지도, release keystore 생성 |
| **Week 2** | iOS Team 교체 + Provisioning, Android keystore + google-services.json 교체, `application-prod.yml` 키 교체, 로컬 prod 빌드 검증 |
| **Week 3** | ~~Google Play 클로즈드 테스트(14일)~~ [법인 면제] → MANNAM 법인 계정 준비되는 대로 프로덕션 제출. 애플은 전환 진행(앱 유지) |
| **Week 4** | 구글 앱 이전(ciccosoft→MANNAM) 또는 0설치 재업로드 결정·실행, Kakao 비즈앱 심사 완료 대기 |
| **Week 5** | 양 스토어 프로덕션 제출/심사, 애플 전환 완료 확인 |
| **Week 6** | 🚀 양 스토어 정식 출시 |
| **Week 8~10** | 개인 계정 정리 (안정화 확인 후) |

---

## 즉시 시작할 3가지 (오늘 안에)

1. **Google Play Console** 회사 가입 ($25, mannamsquare.com 도메인 메일로)
2. **Apple Developer Organization** 가입 ($99)
3. **Kakao 비즈앱 심사** 신청 준비 (회사 Kakao 계정 만든 직후)

이 3개가 클락 시작이라, 다른 모든 작업의 데드라인을 정합니다.

---

## 변경 이력
- 2026-05-19: 최초 작성 (개인→회사 계정 전환 플랜)
- 2026-06-22: 정정 — 구글 14일 룰은 개인 전용(법인 면제), 애플은 신규가입이 아닌 개인→법인 전환(앱 유지), 구글 앱은 ciccosoft 업로드 상태(앱 이전/재업로드 이슈). 상단 정정 배너 + 인라인 표시. 최신 의사결정은 [GoldPet_스토어계정_법인전환_정리_260622.md](./GoldPet_스토어계정_법인전환_정리_260622.md).
