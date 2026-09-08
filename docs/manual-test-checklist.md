# GoldPet 수동 테스트 체크리스트

> **목적**: 자동화 테스트로 커버하기 어려운 네이티브 브릿지·OAuth·FCM·강제 업데이트 등
> 실기기 검증 시나리오를 체계화하여 릴리스 품질을 보장한다.
>
> **관리**: 각 체크리스트 항목에 evidence(스크린샷·로그 캡처)를 첨부하고 PR 본문 또는
> 해당 이슈 코멘트에 링크한다.

---

## 목차

| ID | 이름 | 수행 빈도 |
|----|------|----------|
| [XF-NATIVE-01](#xf-native-01) | Hybrid Bridge Smoke | 매 릴리스 RC |
| [M-AUTH-01](#m-auth-01) | OAuth 4종 로그인 | 회사 계정 전환 직전·직후 |
| [M-PUSH-01](#m-push-01) | FCM 토큰 등록 + 푸시 수신 | 회사 계정 전환 직후 |
| [M-WALK-02](#m-walk-02) | 산책 중 강제 종료 → 데이터 복구 | 산책 관련 PR 머지 후 |
| [M-VERSION-01](#m-version-01) | 강제 업데이트 시나리오 | min_version 변경 배포 후 |
| [M-ADMIN-01](#m-admin-01) | Admin VIEWER role 403 회귀 | 어드민 권한 변경 PR 머지 후 |
| [M-WALK-01](#m-walk-01) | Live Activity + Walk Widget | prod 배포 직후 / iOS 업데이트 후 |
| [M-FILE-01](#m-file-01) | Android WebView FileProvider 업로드 | prod 배포 직후 / Android targetSdk 업데이트 후 |
| [M-WEB-01](#m-web-01) | iOS 키보드 + 댓글 입력 | prod 배포 직후 / iOS WebView 업데이트 후 |
| [XF-08](#xf-08) | PII Phase 2 키 교체 D-day | 키 교체 D-day 1회 |

---

## XF-NATIVE-01

### Hybrid Bridge Smoke — 네이티브 브릿지 5종 동작 확인

| 항목 | 내용 |
|------|------|
| **수행 빈도** | 매 릴리스 RC (Release Candidate) 빌드마다 1회 |
| **수행 트리거** | `app/` 또는 `frontend/src/` 브릿지 관련 코드 변경 PR 머지, 또는 TestFlight 배포 전 |
| **담당자** | iOS/Android 실기기 보유 개발자 |
| **evidence 첨부 방식** | PR 본문 "Evidence" 섹션 또는 이슈 코멘트에 스크린샷·로그 캡처 이미지 첨부 |

#### 사전 조건

- [ ] 실기기 연결 (iOS 15+ 또는 Android 10+)
- [ ] `flutter build apk --debug --dart-define=ENV=dev` 또는 TestFlight 빌드 설치 완료
- [ ] Safari Web Inspector (iOS) 또는 Chrome DevTools Remote Debugging (Android) 연결

#### Evidence 수집 절차

##### (a) iOS 기기 Identifier 확인

- [ ] **iOS**: 설정 → 일반 → 정보 → 식별자(Identifier) 캡처
- [ ] **Android**: 설정 → 휴대전화 정보 → 일련번호 또는 IMEI 캡처
- Evidence 형식: `DEVICE_ID: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX`

##### (b) 빌드 번호 확인

- [ ] TestFlight 빌드 번호 (TestFlight 앱 → 앱 선택 → 버전 정보) 캡처
  또는 `flutter build apk --debug` 출력의 `Built build/app/outputs/flutter-apk/...` 라인 캡처
- Evidence 형식: `BUILD: 1.2.3+45`

##### (c) 로그인 화면 스크린샷

- [ ] 앱 실행 후 로그인 화면 (카카오/네이버/구글/애플 버튼 표시) 스크린샷
- [ ] 소셜 로그인 1종 수행 후 홈 화면 진입 확인

##### (d) 산책 시작 화면 스크린샷

- [ ] 홈 → 산책 탭 → 산책 시작 버튼 클릭 후 GPS 추적 시작 화면 스크린샷
- [ ] 지도에 현재 위치 마커 표시 확인

##### (e) `nativeBridge.startLocationTracking` 콘솔 로그 캡처

- [ ] Safari Web Inspector (iOS): 개발 메뉴 → WebView 선택 → 콘솔 탭
  Chrome Remote Debugging (Android): `chrome://inspect` → WebView inspect → Console
- [ ] 산책 시작 시 `[NativeBridge] startLocationTracking` 또는 동등한 호출 라인 캡처
- Evidence 형식: 콘솔 스크린샷 (타임스탬프 포함)

#### 합격 기준

- [ ] 5종 evidence 모두 첨부됨
- [ ] 로그인 후 홈 화면 정상 진입
- [ ] 산책 화면에서 GPS 좌표 수신 확인 (로그 또는 UI 표시)
- [ ] 브릿지 호출 콘솔 에러 없음

---

## M-AUTH-01

### OAuth 4종 로그인 — 회사 계정 전환 전후 검증

| 항목 | 내용 |
|------|------|
| **수행 빈도** | 회사 계정 전환 직전 1회 + 전환 직후 1회 (총 2회) |
| **수행 트리거** | OAuth 앱 키 교체, Bundle ID 변경, 또는 `application-prod.yml` 소셜 로그인 설정 변경 배포 |
| **담당자** | 인증 담당 개발자 (카카오·네이버·구글·애플 계정 모두 보유) |
| **evidence 첨부 방식** | 각 소셜 로그인 성공 후 홈 화면 스크린샷 4장 (provider 이름 명시) |

#### 사전 조건

- [ ] 카카오 테스트 계정 준비
- [ ] 네이버 테스트 계정 준비
- [ ] 구글 테스트 계정 준비
- [ ] 애플 테스트 계정 준비 (Apple private relay 미사용 계정 권장)
- [ ] 전환 전: 기존 개인 계정 OAuth 앱 키 설정 확인
- [ ] 전환 후: 회사 법인 OAuth 앱 키 설정 확인

#### 체크리스트

- [ ] **카카오 로그인**: 카카오 버튼 탭 → 카카오 OAuth 화면 → 동의 → 홈 진입
  - Evidence: 홈 화면 스크린샷 (상단 프로필 또는 닉네임 표시)
- [ ] **네이버 로그인**: 네이버 버튼 탭 → 네이버 OAuth 화면 → 동의 → 홈 진입
  - Evidence: 홈 화면 스크린샷
- [ ] **구글 로그인**: 구글 버튼 탭 → 구글 계정 선택 → 홈 진입
  - Evidence: 홈 화면 스크린샷
- [ ] **애플 로그인**: 애플 버튼 탭 → Face ID/Touch ID 인증 → 홈 진입
  - Evidence: 홈 화면 스크린샷
- [ ] 전환 전후 동일 계정으로 재로그인 시 기존 프로필 데이터 유지 확인
- [ ] 토큰 갱신(/auth/refresh) 동작 확인 (앱 백그라운드 → 포그라운드 전환)

#### 합격 기준

- [ ] 4종 OAuth 모두 홈 화면까지 정상 진입
- [ ] 401 / 403 에러 없음
- [ ] 프로필 잠금 상태(profileLockedAt) 기존 값 유지

---

## M-PUSH-01

### FCM 토큰 등록 + 푸시 수신 — 회사 계정 전환 후 검증

| 항목 | 내용 |
|------|------|
| **수행 빈도** | 회사 계정 전환 직후 1회; `firebase-service-account.json` 교체 시마다 |
| **수행 트리거** | Firebase 프로젝트 또는 서비스 계정 변경, FCM 관련 코드 수정 PR 머지 |
| **담당자** | FCM 설정 담당 개발자 |
| **evidence 첨부 방식** | (1) 서버 로그의 FCM 토큰 등록 성공 라인, (2) 기기 알림 수신 스크린샷 |

#### 사전 조건

- [ ] 새 `firebase-service-account.json` 서버 클래스패스 또는 워킹 디렉터리 배포 확인
- [ ] `PUT /api/v1/users/me/fcm-token` 엔드포인트 동작 확인
- [ ] Android: POST_NOTIFICATIONS 권한 허용

#### 체크리스트

- [ ] 앱 설치 후 최초 로그인 시 FCM 토큰 자동 등록 확인
  - Evidence: 서버 로그 `FCM token registered for user ...` 라인 캡처
- [ ] Admin 콘솔 또는 Firebase Console에서 테스트 푸시 발송
- [ ] 앱 포그라운드 상태에서 인앱 알림 수신 확인
  - Evidence: 알림 토스트 또는 알림 배너 스크린샷
- [ ] 앱 백그라운드 상태에서 OS 푸시 알림 수신 확인
  - Evidence: 시스템 알림 트레이 스크린샷
- [ ] 다중 기기(최대 5대) 등록 시 모든 기기 수신 확인 (가능한 경우)

#### 합격 기준

- [ ] 토큰 등록 API 200 OK
- [ ] 포그라운드·백그라운드 각 1건 이상 수신 성공
- [ ] 알림 탭 시 해당 화면으로 딥링크 이동 (해당되는 경우)

---

## M-WALK-02

### 산책 중 강제 종료 → 데이터 복구 확인

| 항목 | 내용 |
|------|------|
| **수행 빈도** | 산책 관련 PR(`walk_screen.dart`, `WalkService`, walk API) 머지 후 1회 |
| **수행 트리거** | `dispose()` 가드, 산책 저장 로직, Navigator 변경 관련 코드 수정 |
| **담당자** | Flutter 개발자 |
| **evidence 첨부 방식** | (1) 강제 종료 전 산책 화면 스크린샷, (2) 재실행 후 산책 기록 목록 스크린샷 |

#### 배경

`walk_screen.dart`의 `dispose()`는 `_stopWalkWidget()`(알림·Live Activity 중단)만 호출하며,
`_stopWalk()`(서버 저장)는 직접 호출하지 않는다.
뒤로가기 확인 다이얼로그가 정상 경로를 보호하지만, 프로그래밍적 `Navigator.pop()` 또는
OS 백 제스처 우회 시 데이터 유실 위험이 있다.

#### 체크리스트

**시나리오 A — 정상 종료**

- [ ] 산책 시작 → 5분 이상 GPS 추적 → 산책 종료 버튼 탭
- [ ] 산책 기록 저장 확인 (산책 목록 화면에서 신규 항목 표시)
  - Evidence: 산책 기록 목록 스크린샷 (날짜·거리 표시)

**시나리오 B — 강제 종료 (OS kill)**

- [ ] 산책 시작 → 2분 이상 GPS 추적 → 앱 강제 종료 (태스크 매니저에서 스와이프 아웃)
- [ ] 앱 재실행 후 산책 목록 확인
  - 데이터 유실 시: 미완료 산책 복구 UI 또는 부분 저장 확인
  - Evidence: 재실행 후 산책 목록 스크린샷

**시나리오 C — 강제 업데이트 다이얼로그 중 산책 중단**

- [ ] 산책 중 `ForceUpdateDialog` 표시 시나리오 시뮬레이션
  (dev 환경 min_version 강제 설정)
- [ ] 업데이트 다이얼로그 표시 → 앱스토어 이동 후 복귀 시 산책 상태 확인
  - Evidence: 다이얼로그 스크린샷 + 복귀 후 화면 스크린샷

#### 합격 기준

- [ ] 시나리오 A: 정상 저장 확인
- [ ] 시나리오 B: 데이터 유실 없거나 복구 UI 제공
- [ ] 시나리오 C: 산책 데이터 보존 또는 명시적 경고 표시

---

## M-VERSION-01

### 강제 업데이트 시나리오 — ForceUpdateDialog 동작 확인

| 항목 | 내용 |
|------|------|
| **수행 빈도** | `min_version` 변경 배포 시마다; 앱 버전 범프 RC마다 1회 |
| **수행 트리거** | `AppVersionController`/`AppVersionService` 변경, min_version 시스템 설정 변경 |
| **담당자** | 릴리스 담당 개발자 |
| **evidence 첨부 방식** | ForceUpdateDialog 및 SoftUpdateDialog 스크린샷 |

#### 사전 조건

- [ ] dev 환경 `system_settings` 테이블에서 `app.min_version` 값을 현재 앱보다 높게 설정
  ```sql
  UPDATE system_settings SET value = '99.0.0' WHERE key = 'app.min_version';
  ```
- [ ] `/api/v1/app/version` 엔드포인트 permitAll 설정 확인 (`SecurityConfig`)

#### 체크리스트

**강제 업데이트 (min_version > current)**

- [ ] 앱 실행 시 `ForceUpdateDialog` 표시 (dismiss 불가)
  - Evidence: ForceUpdateDialog 스크린샷
- [ ] "업데이트" 버튼 탭 시 앱스토어/플레이스토어 이동 확인
- [ ] 다이얼로그 외부 탭 또는 뒤로가기로 닫히지 않음 확인

**소프트 업데이트 (latest_version > current, min_version ≤ current)**

- [ ] `SoftUpdateDialog` 표시 (24시간 쿨다운 적용)
  - Evidence: SoftUpdateDialog 스크린샷
- [ ] "나중에" 선택 시 24시간 동안 재표시 안 됨 확인
- [ ] 앱 재실행 후 쿨다운 내 미표시 확인

**정상 버전**

- [ ] min_version 원복 후 업데이트 다이얼로그 미표시 확인
  ```sql
  UPDATE system_settings SET value = '1.0.0' WHERE key = 'app.min_version';
  ```

#### 합격 기준

- [ ] ForceUpdateDialog: non-dismissible 동작 확인
- [ ] SoftUpdateDialog: 24h 쿨다운 동작 확인
- [ ] 정상 버전에서 다이얼로그 미표시

---

## M-ADMIN-01

### Admin VIEWER Role 403 회귀 방지

| 항목 | 내용 |
|------|------|
| **수행 빈도** | 어드민 권한(Role) 관련 PR 머지 후 1회 |
| **수행 트리거** | `AdminRole`, `SecurityConfig`, admin 권한 체크 로직 변경; BUG-05 회귀 방지 |
| **담당자** | 어드민 기능 담당 개발자 |
| **evidence 첨부 방식** | (1) VIEWER 계정 로그인 스크린샷, (2) backup-jobs 페이지 403 에러 스크린샷 |

#### 배경

BUG-05: VIEWER role 어드민 계정이 `/admin/backup-jobs` 페이지에 접근 가능했던 버그.
해당 페이지는 SUPER_ADMIN 또는 ADMIN role 전용이어야 한다.

#### 사전 조건

- [ ] VIEWER role 테스트 어드민 계정 생성 또는 확인
  ```sql
  -- 확인
  SELECT id, email, role FROM admin_users WHERE role = 'VIEWER' LIMIT 1;
  ```
- [ ] 어드민 서버(`/admin` 경로) 접근 가능 환경 준비

#### 체크리스트

**VIEWER role 접근 제한 확인**

- [ ] VIEWER 계정으로 어드민 로그인
  - Evidence: 어드민 로그인 후 대시보드 스크린샷 (role 표시 확인)
- [ ] `/admin/backup-jobs` 직접 URL 접근 시도
  - Evidence: 403 Forbidden 에러 화면 스크린샷 (또는 리다이렉트 화면)
- [ ] `/admin/users` (VIEWER 허용 페이지) 정상 접근 확인
  - Evidence: 유저 목록 페이지 스크린샷

**ADMIN role 정상 접근 확인**

- [ ] ADMIN role 계정으로 `/admin/backup-jobs` 접근 시 정상 표시 확인
  - Evidence: backup-jobs 페이지 정상 표시 스크린샷

**API 레벨 검증**

- [ ] VIEWER 토큰으로 `GET /api/admin/backup-jobs` 직접 호출 시 403 응답 확인
  ```bash
  curl -H "Authorization: Bearer <VIEWER_TOKEN>" \
    https://api.mannamsquare.com/api/admin/backup-jobs
  # Expected: HTTP 403
  ```

#### 합격 기준

- [ ] VIEWER: `/admin/backup-jobs` 접근 불가 (403 또는 리다이렉트)
- [ ] ADMIN: `/admin/backup-jobs` 접근 가능
- [ ] API 레벨 403 확인

---

## M-WALK-01

### Live Activity + Walk Widget — iOS 산책 위젯 동작 확인 (15분)

| 항목 | 내용 |
|------|------|
| **수행 빈도** | prod 배포 직후 1회; iOS 메이저 업데이트 후 |
| **수행 트리거** | `walk_screen.dart`, Live Activity 관련 Flutter/Swift 코드 변경 PR 머지, 또는 prod 빌드 배포 |
| **담당자** | iOS 실기기 보유 개발자 |
| **evidence 첨부 방식** | (1) Lock Screen Live Activity 스크린샷, (2) Dynamic Island 스크린샷, (3) 홈 위젯 갱신 스크린샷 |

#### 사전 조건

- [ ] iOS 16.1+ 실기기 (Live Activity 지원 OS)
- [ ] 홈 화면에 GoldPet Walk 위젯 추가 완료
- [ ] prod 또는 dev 빌드 설치 (`flutter build ipa --dart-define=ENV=dev`)

#### 체크리스트

**시나리오 A — Live Activity (Lock Screen)**

- [ ] 산책 시작 → 기기 잠금 → Lock Screen에서 산책 진행 시간·거리 표시 확인
  - Evidence: Lock Screen Live Activity 스크린샷 (시간·거리 수치 포함)
- [ ] 산책 종료 → Lock Screen Live Activity 사라짐 또는 완료 상태로 전환 확인

**시나리오 B — Dynamic Island (iPhone 14 Pro+)**

- [ ] 산책 시작 → Dynamic Island에 걸음수 또는 타이머 표시 확인
  - Evidence: Dynamic Island 확장 뷰 스크린샷
- [ ] Dynamic Island 탭 → 산책 화면으로 딥링크 이동 확인

**시나리오 C — 홈 위젯 데이터 갱신**

- [ ] 산책 종료 후 홈 화면 위젯에 최신 산책 데이터(날짜·거리) 갱신 확인
  - Evidence: 홈 위젯 스크린샷 (최신 수치 확인)

#### 합격 기준

- [ ] Lock Screen Live Activity 표시 정상 (시나리오 A)
- [ ] Dynamic Island 표시 정상 (iPhone 14 Pro+ 기기에서만, 시나리오 B)
- [ ] 위젯 데이터 산책 종료 후 갱신 확인 (시나리오 C)
- [ ] 알림·Live Activity 관련 콘솔 에러 없음

---

## M-FILE-01

### Android WebView FileProvider 업로드 — content:// URI 검증 (10분)

| 항목 | 내용 |
|------|------|
| **수행 빈도** | prod 배포 직후 1회; Android targetSdk 변경 또는 FileProvider 설정 변경 시 |
| **수행 트리거** | `AndroidManifest.xml`, `file_paths.xml`, `MainActivity.kt` MethodChannel, `S3_PUBLIC_ENDPOINT` 환경 변수 변경 |
| **담당자** | Android 실기기 보유 개발자 |
| **evidence 첨부 방식** | (1) 이미지 선택 후 업로드 완료 화면 스크린샷, (2) 콘솔 또는 네트워크 탭에서 content:// URI 확인 캡처 |

#### 사전 조건

- [ ] Android 10+ 실기기
- [ ] `S3_PUBLIC_ENDPOINT`가 기기에서 접근 가능한 IP로 설정 (로컬 dev: LAN IP, prod: CDN URL)
- [ ] `AndroidManifest.xml` FileProvider 설정 및 `res/xml/file_paths.xml` 존재 확인

#### 체크리스트

**시나리오 A — 프로필 이미지 업로드 (갤러리)**

- [ ] 마이페이지 → 프로필 편집 → 이미지 변경 → 갤러리에서 사진 선택
- [ ] Page refresh 없이 이미지 크롭/선택 화면으로 전환 확인
- [ ] 업로드 완료 후 프로필 이미지 변경 확인
  - Evidence: 업로드 완료 후 프로필 화면 스크린샷

**시나리오 B — 산책 사진 업로드 (카메라)**

- [ ] 산책 종료 후 사진 업로드 화면 → 카메라 촬영 선택
- [ ] 카메라 앱 실행 → 촬영 → Page refresh 없이 업로드 화면 복귀 확인
- [ ] 업로드 완료 후 산책 기록에 사진 표시 확인
  - Evidence: 산책 기록 사진 표시 스크린샷

**시나리오 C — URI 타입 확인**

- [ ] Chrome Remote Debugging (`chrome://inspect`) 콘솔에서 파일 선택 이벤트 로그 확인
- [ ] `file://` URI 대신 `content://` URI 사용 확인 (Page refresh 미발생)
  - Evidence: 콘솔 로그 스크린샷 또는 네트워크 탭 multipart 업로드 요청 캡처

#### 합격 기준

- [ ] 갤러리·카메라 선택 시 Page refresh 없음
- [ ] `content://` URI로 파일 전달 확인
- [ ] 프로필·산책 사진 서버 저장 및 화면 표시 정상
- [ ] 업로드 관련 콘솔 에러 없음

---

## M-WEB-01

### iOS 키보드 + 댓글 입력 — WKWebView flex container 동작 (10분)

| 항목 | 내용 |
|------|------|
| **수행 빈도** | prod 배포 직후 1회; iOS 메이저 업데이트 후 |
| **수행 트리거** | `CommunityDetailPage.css`, `ChatDetailPage.css`, `--app-height` 또는 `visualViewport` 관련 코드 변경 |
| **담당자** | iOS 실기기 보유 개발자 |
| **evidence 첨부 방식** | 댓글 입력 중 키보드가 올라온 상태에서 comment_submit이 키보드 바로 위에 표시된 스크린샷 |

#### 배경

iOS WKWebView에서 `position: fixed; bottom: 0` + document scroll 조합은 키보드 표시 시
comment_submit 버튼이 키보드 아래로 숨는 버그가 발생한다.
`flex container + --app-height + visualViewport.resize` 패턴으로 수정되어 있으며,
iOS 업데이트 또는 관련 CSS 변경 시 회귀 여부를 확인해야 한다.

#### 사전 조건

- [ ] iOS 15+ 실기기 (Safari/WKWebView)
- [ ] 커뮤니티 게시글 1건 이상 존재 (댓글 입력 가능 상태)

#### 체크리스트

- [ ] 커뮤니티 → 게시글 상세 진입 → 댓글 입력창 탭
  - 키보드 상승 시 comment_submit(댓글 전송 버튼)이 키보드 바로 위에 표시됨 확인
  - Evidence: 키보드 올라온 상태 스크린샷 (버튼 위치 포함)
- [ ] 댓글 입력 후 전송 버튼 탭 → 댓글 등록 확인
- [ ] 키보드 내리기(return 또는 외부 탭) → comment_submit 하단 원위치 확인
- [ ] 채팅 상세 페이지에서도 동일 동작 확인 (ChatDetailPage)
  - Evidence: 채팅 키보드 상승 상태 스크린샷

#### 합격 기준

- [ ] 키보드 상승 시 댓글/채팅 입력 버튼 가려지지 않음
- [ ] 댓글·채팅 전송 정상 동작
- [ ] 키보드 내린 후 레이아웃 복원 확인
- [ ] `position: fixed` 버그 회귀 없음

---

## XF-08

### PII Phase 2 키 교체 D-day — 복호화 연속성 확인 (20분)

| 항목 | 내용 |
|------|------|
| **수행 빈도** | PII 키 교체 D-day **1회만** 수행 |
| **수행 트리거** | dev 듀얼키 인프라 배포 완료 상태에서 Phase 2 실제 키 교체 진행 시 |
| **담당자** | PII 로테이션 담당 개발자 + 인프라 담당자 |
| **evidence 첨부 방식** | (1) 키 교체 전 신규 가입 스크린샷, (2) 키 교체 직후 로그인·프로필 정상 표시 스크린샷, (3) 서버 복호화 성공 로그 |

#### 배경

PII Key Rotation Phase 1에서 dev 듀얼키 인프라 배포가 완료되었다(2026-04-19).
Phase 2는 실제 키 교체로, 교체 직전에 암호화된 기존 데이터가 교체 후에도 정상 복호화되는지
연속성을 반드시 검증해야 한다.

#### 사전 조건

- [ ] dev 환경 듀얼키 인프라 배포 완료 확인 (`project_pii_key_rotation.md` 참조)
- [ ] 교체 전 PII 암호화 키(OLD_KEY) 백업 확인
- [ ] 새 PII 키(NEW_KEY) 준비 및 롤백 플랜 확인
- [ ] Admin 콘솔 또는 DB 접근 권한 보유

#### 체크리스트

**Phase A — 키 교체 직전 기준점 확보**

- [ ] 신규 테스트 계정 가입 (이메일·전화번호 입력)
  - Evidence: 가입 완료 화면 스크린샷 (이메일·전화번호 표시)
- [ ] 기존 테스트 계정 1건 로그인 → 프로필(이메일·전화번호) 정상 표시 확인
  - Evidence: 프로필 화면 스크린샷 (마스킹 또는 일부 표시)
- [ ] DB에서 암호화된 컬럼 값 캡처 (base64 등 암호문 형태)
  ```sql
  SELECT id, email_encrypted, phone_encrypted FROM users WHERE id = <TEST_USER_ID>;
  ```

**Phase B — 키 교체 진행**

- [ ] NEW_KEY 서버 환경 변수 반영 및 서버 재시작
- [ ] 서버 로그에서 키 교체 완료 및 듀얼키 복호화 활성화 확인

**Phase C — 키 교체 직후 복호화 연속성 검증**

- [ ] Phase A에서 가입한 신규 계정으로 로그인 → 이메일·전화번호 정상 복호화 확인
  - Evidence: 로그인 후 프로필 화면 스크린샷 (Phase A와 동일 데이터)
- [ ] Phase A 기존 테스트 계정 재로그인 → 프로필 정상 표시 확인
  - Evidence: 프로필 화면 스크린샷
- [ ] 서버 로그에서 `PII decryption success` 또는 동등 로그 확인
  - Evidence: 서버 로그 캡처 (user id + 성공 라인)
- [ ] 토큰 갱신 (`/auth/refresh`) 후에도 PII 정상 복호화 확인

**Phase D — 롤백 검증 (선택)**

- [ ] OLD_KEY로 롤백 시 복호화 정상 여부 확인 (필요 시)

#### 합격 기준

- [ ] 교체 전 암호화 데이터가 교체 후에도 정상 복호화
- [ ] 신규 가입 데이터(NEW_KEY 암호화)도 정상 복호화
- [ ] 서버 에러 로그 없음 (복호화 실패 없음)
- [ ] 토큰 갱신 후에도 PII 정상

---

## 회차 룰

### 수행 시점별 필수 항목

| 수행 시점 | 필수 항목 | 예상 시간 |
|----------|----------|---------|
| **회사 계정 전환 직전 (D-7)** | M-AUTH-01, M-PUSH-01 | ~50분 |
| **회사 계정 전환 직후 (D+0)** | M-AUTH-01, M-PUSH-01, XF-08 (해당 시) | ~70분 |
| **prod 배포 직후** | M-WALK-01, M-FILE-01, M-WEB-01 | ~35분 |
| **격주 정기** | P2 smoke (`scripts/smoke/{gamification,checkin,health,report}.sh`) | ~15분 |
| **PII 키 교체 D-day** | XF-08 | ~20분 |
| **수시** | M-VERSION-01, M-WALK-02 (관련 코드 변경 시) | ~각 15–20분 |

### 풀스캔 순서 (회사 계정 전환 직전+직후)

```
D-7:  XF-NATIVE-01 → M-AUTH-01 → M-PUSH-01
D+0:  M-AUTH-01 → M-PUSH-01 → XF-08
      → M-WALK-01 → M-FILE-01 → M-WEB-01 → M-VERSION-01
```

**총 검토 시간**: ~125분 (D-7 + D+0 풀스캔 기준)

### 항목별 소요 시간 요약

| 항목 | 소요 시간 |
|------|---------|
| XF-NATIVE-01 | ~15분 |
| M-AUTH-01 | ~30분 |
| M-PUSH-01 | ~20분 |
| M-WALK-02 | ~15분 |
| M-VERSION-01 | ~15분 |
| M-ADMIN-01 | ~10분 |
| M-WALK-01 | ~15분 |
| M-FILE-01 | ~10분 |
| M-WEB-01 | ~10분 |
| XF-08 | ~20분 |

---

## Evidence 첨부 가이드

### 스크린샷 명명 규칙

```
{CHECKLIST_ID}_{STEP}_{YYYYMMDD}.png
예: XF-NATIVE-01_e_console_20260525.png
    M-AUTH-01_kakao_home_20260525.png
```

### 첨부 위치

1. **PR 본문**: PR 설명의 "## Evidence" 섹션에 이미지 드래그 앤 드롭
2. **GitHub Issue**: 관련 이슈 코멘트에 첨부 후 PR에서 링크
3. **Slack**: `#goldpet-releases` 채널에 스레드로 공유 후 URL 기록

### 로그 캡처 가이드

- **Safari Web Inspector**: 개발 메뉴 → \[기기명\] → \[WebView\] → 콘솔 탭 스크린샷
- **Chrome Remote Debugging**: `chrome://inspect/#devices` → inspect → Console 탭
- 로그 캡처 시 타임스탬프가 보이도록 콘솔 설정 유지
