# 회사 계정 전환 진행 추적 (만남스퀘어 법인)

> 작성일: 2026-05-26
> 근거 plan: `docs/GoldPet_상용출시_회사계정전환_플랜.md`
> 목표 출시: 7월 초
> Critical path: Kakao 비즈앱 심사 (1-2주)

---

## Week 0 (오늘 ~ 5/30) — 즉시 시작 3가지

| # | 항목 | URL | 비용 | 상태 | 담당 | 완료일 |
|---|---|---|---|---|---|---|
| 1 | **Google Play Console Organization 가입** | https://play.google.com/console/signup | $25 (일회성) | ⏳ | Ops | |
| 2 | **Apple Developer Program Organization 가입** | https://developer.apple.com/programs/enroll/ | $99/년 | ⏳ | Ops | |
| 3 | **회사 도메인 메일 분리 생성** (`apple-dev@/google-play@/firebase@mannamsquare.com`) | (회사 메일 시스템) | — | ⏳ | Ops | |
| 4 | **Kakao 비즈앱 심사 준비** (1-2주 소요, **critical path**) | https://developers.kakao.com | — | ⏳ | Ops | |

**Week 0 출구 조건**: 1-3번 가입 신청 완료, 4번 심사 신청 서류 작성.

---

## Week 1 (6/1 ~ 6/6) — 외부 승인 대기 + Phase 2 시작

| # | 항목 | 의존성 | 상태 | 담당 | 완료일 |
|---|---|---|---|---|---|
| 5 | Firebase 새 프로젝트 (`goldpet-prod`) 생성 + Android/iOS 앱 등록 | — | ⏳ | BE Lead | |
| 6 | APNs Key (.p8) 발급 → Firebase 콘솔 업로드 | Apple Dev 승인 후 | ⏳ | iOS Lead | |
| 7 | AWS prod 계정 생성 + S3 `goldpet-prod` 버킷 + IAM 사용자 | — | ⏳ | Ops | |
| 8 | Kakao 비즈앱 심사 **신청** | Kakao 회사 계정 완료 후 | ⏳ | Ops | |
| 9 | Naver Developers (로그인) Client ID/Secret 발급 | — | ⏳ | BE Lead | |
| 10 | Naver Cloud Maps Client ID 발급 | — | ⏳ | BE Lead | |
| 11 | Google OAuth Client ID 발급 (Web/Android/iOS) | Firebase 생성 후 | ⏳ | BE Lead | |
| 12 | Apple Sign-in Service ID + Key (.p8) 발급 | Apple Dev 승인 후 | ⏳ | iOS Lead | |
| 13 | Android release keystore 생성 + SHA-1/256 추출 | — | ⏳ | Flutter Lead | |

**Week 1 출구 조건**: Firebase 활성, Kakao 심사 진행 중, 모든 3rd-party 키 발급 완료.

---

## Week 2 (6/8 ~ 6/13) — 앱 빌드 설정 교체

### iOS (`app/ios/`)
| # | 항목 | 상태 |
|---|---|---|
| 14 | Apple Developer Portal에서 회사 Team으로 App ID 새 등록 (`com.mannam.goldpet`, `com.mannam.goldpet.WalkActivityWidget`) | ⏳ |
| 15 | Capabilities 활성화 (Push, Sign in with Apple, App Groups for Live Activity) | ⏳ |
| 16 | Distribution Certificate + Provisioning Profile 새 발급 | ⏳ |
| 17 | `Runner.xcodeproj/project.pbxproj`의 `DEVELOPMENT_TEAM` 회사 Team ID로 전부 교체 (Runner/RunnerTests/WalkActivityWidget) | ⏳ |
| 18 | `ios/Runner/GoogleService-Info.plist` 교체 | ⏳ |
| 19 | `ios/Runner/Info.plist` URL Schemes 교체 (Kakao/Naver/Google) | ⏳ |
| 20 | `ios/fastlane/Appfile` `team_id`/`apple_id` 교체 | ⏳ |

### Android (`app/android/`)
| # | 항목 | 상태 |
|---|---|---|
| 21 | `app/google-services.json` 교체 | ⏳ |
| 22 | `key.properties` 새 keystore 경로/비번 설정 | ⏳ |
| 23 | Kakao native app key 교체 (`strings.xml` 또는 BuildConfig) | ⏳ |
| 24 | `AndroidManifest.xml` `kakao{NEW_APP_KEY}` redirect scheme 갱신 | ⏳ |
| 25 | Naver Maps client ID 교체 | ⏳ |

### Backend (`api/`)
| # | 항목 | 상태 |
|---|---|---|
| 26 | `application-prod.yml` Kakao/Naver/Google/Apple OAuth 새 값 | ⏳ |
| 27 | `application-prod.yml` AWS S3 prod 자격증명 | ⏳ |
| 28 | `firebase-service-account.json` 새 파일 Jenkins 시크릿 등록 | ⏳ |
| 29 | Apple Sign-in 새 Service/Team/Key ID 반영 | ⏳ |

### Frontend / Admin
| # | 항목 | 상태 |
|---|---|---|
| 30 | `frontend/.env.prod` Kakao/Naver/Google/Naver Maps 키 교체 | ⏳ |
| 31 | `admin/.env.prod` 동일 | ⏳ |

### 검증
| # | 항목 | 상태 |
|---|---|---|
| 32 | 로컬 `flutter build apk --release --dart-define=ENV=prod` 성공 | ⏳ |
| 33 | 로컬 `flutter build ipa --dart-define=ENV=prod` 성공 | ⏳ |
| 34 | Backend prod 빌드 + Sentry init 이벤트 도달 확인 | ⏳ |

**Week 2 출구 조건**: 회사 키로 prod 빌드 성공, dev 환경 영향 0.

---

## Week 3 (6/15 ~ 6/20) — 스토어 등록 + closed test 시작

| # | 항목 | 상태 |
|---|---|---|
| 35 | Google Play AAB 업로드 → **클로즈드 테스트 트랙** | ⏳ |
| 36 | 테스터 12명 이상 모집 (TestFlight 8 + 동료 4) | ⏳ |
| 37 | **Play 14일 카운트다운 시작** (옵트인 + 실사용 의무) | ⏳ |
| 38 | App Store Connect 새 앱 생성 (`com.mannam.goldpet`) | ⏳ |
| 39 | TestFlight 빌드 업로드 + 기존 8명 테스터 재초대 | ⏳ |
| 40 | App Privacy 양식 작성 (위치/사진/카메라/연락처/사용 데이터/진단) | ⏳ |
| 41 | `Info.plist` 사용 사유 문구 검토 (`NSLocationWhenInUseUsageDescription` 등) | ⏳ |

**Week 3 출구 조건**: Play 14일 시작, TestFlight 8명 재초대 완료.

---

## Week 4 (6/22 ~ 6/27) — 심사 제출

| # | 항목 | 상태 |
|---|---|---|
| 42 | App Store 심사 제출 (24-48h) | ⏳ |
| 43 | Kakao 비즈앱 심사 완료 확인 | ⏳ |
| 44 | TestFlight 8명에게 "재로그인 필요" 공지 메일 | ⏳ |
| 45 | 강제 업데이트 시스템 `app_versions` 테이블 prod 최소 버전 사전 입력 | ⏳ |

**Week 4 출구 조건**: App Store 심사 진행 중, Kakao 비즈앱 승인.

---

## Week 5 (6/29 ~ 7/4) — 프로덕션 승격 + 심사 통과

| # | 항목 | 상태 |
|---|---|---|
| 46 | Google Play 14일 완료 → 프로덕션 트랙 승격 신청 | ⏳ |
| 47 | App Store 심사 통과 + 출시 보류 (수동 release) | ⏳ |
| 48 | 백엔드 prod 서버 키 회사 명의 교체 완료 최종 확인 | ⏳ |
| 49 | Sentry critical 0건 + Crash-free ≥99.5% 최종 확인 | ⏳ |

**Week 5 출구 조건**: 양 스토어 출시 승인 완료, 출시 보류 상태.

---

## Week 6 (7/6 ~ 7/11) — 🚀 정식 출시

| # | 항목 | 상태 |
|---|---|---|
| 50 | App Store 수동 release | ⏳ |
| 51 | Google Play 프로덕션 트랙 release | ⏳ |
| 52 | 출시 공지 (마케팅 + SNS) | ⏳ |
| 53 | 일일 KPI 리뷰 미팅 D+1, D+7, D+14 | ⏳ |

---

## Week 8-10 — Post-launch 정리

| # | 항목 | 상태 |
|---|---|---|
| 54 | 개인 Apple Developer 갱신 중지 ($99/년 절약) | ⏳ |
| 55 | 개인 Firebase 프로젝트 (사용자 0명 확인 후) 삭제 | ⏳ |
| 56 | 개인 Kakao/Naver 앱 비활성화 | ⏳ |
| 57 | 회사 계정 2FA 백업 코드 보안 담당자와 공유 | ⏳ |
| 58 | Jenkins prod 환경변수 전체 회사 키 교체 완료 확인 | ⏳ |

---

## 진행률

- Week 0: 0/4 ⏳
- Week 1: 0/9 ⏳
- Week 2: 0/21 ⏳
- Week 3: 0/7 ⏳
- Week 4: 0/4 ⏳
- Week 5: 0/4 ⏳
- Week 6: 0/4 ⏳
- Week 8-10: 0/5 ⏳

**전체**: 0 / 58 (0%)

---

## 흔한 함정 (재공지)

1. **Bundle ID 변경 유혹 금지** — `com.mannam.goldpet` 그대로.
2. **개인 Gmail로 회사 계정 만들기 금지** — 반드시 `@mannamsquare.com` 메일.
3. **Google Play Console 14일 룰 무시 금지** — 신규 법인은 무조건 적용.
4. **Kakao 비즈앱 심사 안 거치고 동의항목 사용 금지** — Week 1 안에 신청.

---

## 변경 이력
- 2026-05-26: 최초 작성 (Sprint 0 추적 시작).
