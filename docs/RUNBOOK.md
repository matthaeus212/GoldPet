# GoldPet 운영 RUNBOOK

> **목적**: Feature Test 운영 룰 · 회사 계정 전환 타임라인 · 백업 협력자 가이드를
> 단일 문서로 통합하여 1인 운영자 부재·전환 실패 시에도 작업이 재개 가능하도록 한다.
>
> **갱신 정책**: Phase 완료 시 섹션 7 진행 현황 업데이트. 의사결정 변경 시 섹션 6 기록.
> **작성일**: 2026-05-25

---

## 목차

1. [Feature Test 회차 룰](#1-feature-test-회차-룰)
2. [D-7 게이트 (Phase 1 안전망 미완 차단)](#2-d-7-게이트)
3. [D-14 백업 협력자 준비](#3-d-14-백업-협력자-준비)
4. [회사 계정 전환 타임라인](#4-회사-계정-전환-타임라인)
5. [OAuth Provider 테스트 계정 가이드](#5-oauth-provider-테스트-계정-가이드)
6. [Open Q-NEW 결정 기록](#6-open-q-new-결정-기록)
7. [Phase별 진행 현황 추적](#7-phase별-진행-현황-추적)

---

## 1. Feature Test 회차 룰

### 1-1. 수행 빈도 매트릭스

| 트리거 | 수행 항목 | 비고 |
|--------|----------|------|
| **D-7 (전환 7일 전)** | M-AUTH-01 + M-PUSH-01 전수 | 게이트 통과 여부 판단 |
| **D-0 (전환 당일)** | M-AUTH-01 + M-PUSH-01 + XF-08(해당 시) | 전환 직전·직후 각 1회 |
| **D+1 (전환 다음날)** | M-AUTH-01 + M-PUSH-01 재실행 | evidence 첨부 의무 |
| **매 릴리스 RC** | XF-NATIVE-01 | app/ 또는 frontend/ 브릿지 변경 시 |
| **산책 PR 머지 후** | M-WALK-02 | 산책 관련 코드 변경 직후 |
| **min_version 배포 후** | M-VERSION-01 | 강제 업데이트 설정 변경 시 |
| **어드민 권한 PR 머지 후** | M-ADMIN-01 | Admin 권한 로직 변경 시 |
| **격주 월요일** | smoke 6건 자동화 확인 | CI 결과 슬랙·메일 확인 |

### 1-2. 자동화 게이트 매주 실행 룰

자동화 테스트는 다음 3계층으로 구분하며, **매주 월요일 00:00 KST** Jenkins 스케줄 트리거로 실행한다.

| 계층 | 테스트 종류 | 최소 통과 기준 | 실패 시 행동 |
|------|-----------|--------------|-------------|
| **Playwright E2E** | frontend + admin 5+2 spec | 전체 PASS | PR 머지 차단 |
| **Integration Test (IT)** | WebSocketRace + AccountLinking + DormantRecovery | 전체 PASS | 배포 파이프라인 중단 |
| **Smoke** | P0 도메인 6건 | 전체 PASS | on-call 알림 |

> **smoke 자동화 미완 구간 처리**: smoke P2 항목(gamification/checkin/health/report)이
> 아직 미완인 경우, 해당 항목은 수동 체크로 대체하고 RUNBOOK 섹션 7에 "수동 대체" 표기.

### 1-3. Evidence 관리 룰

- 모든 수동 체크리스트는 스크린샷 또는 로그 캡처를 **PR 본문 Evidence 섹션** 또는
  **이슈 코멘트**에 첨부한다.
- 파일명 규칙: `{ID}_{YYYYMMDD}_{provider}.png` (예: `M-AUTH-01_20260701_kakao.png`)
- evidence 없이 PASS 체크 금지. 미첨부 시 회차 무효.

---

## 2. D-7 게이트

> 회사 계정 전환(D-0) **7일 전** 게이트 측정을 수행한다.
> 아래 조건 중 하나라도 미충족이면 전환을 **자동 연기**한다.

### 2-1. 게이트 통과 조건

| # | 조건 | 측정 방법 | 통과 기준 |
|---|------|----------|----------|
| G-1 | smoke 6건 PASS | Jenkins smoke job 최신 결과 확인 | 6/6 PASS |
| G-2 | contract test PASS | `./gradlew test --tests "*ContractTest*"` | 0 FAIL |
| G-3 | WALK-05 dispose guard 구현 완료 | `git log --grep="dispose.*walk\|walk.*dispose"` | commit 존재 |
| G-4 | M-AUTH-01 전수 1회 완료 | evidence 첨부 여부 | 4종 스크린샷 존재 |
| G-5 | 백업 협력자 계정 공유 완료 | 1Password/BitWarden vault 항목 확인 | 4개 항목 존재 |

### 2-2. 게이트 자동 연기 트리거

```bash
# D-7 게이트 측정 스크립트 (로컬 실행)
#!/usr/bin/env bash
set -e

echo "=== GoldPet D-7 Gate Check ==="

# G-3: WALK-05 dispose 커밋 확인
DISPOSE_COMMIT=$(git log --oneline --grep="dispose.*walk\|walk.*dispose" | head -1)
if [ -z "$DISPOSE_COMMIT" ]; then
  echo "[FAIL] G-3: WALK-05 dispose guard 커밋 없음 — 전환 연기"
  exit 1
fi

# G-1: smoke 결과는 Jenkins 콘솔에서 수동 확인 (API 미연동)
echo "[INFO] G-1: Jenkins smoke job 결과를 수동으로 확인하세요."
echo "       URL: https://ci.mannamsquare.com/job/goldpet-smoke/"

echo "=== 게이트 측정 완료. 나머지 조건(G-2~G-5)은 체크리스트 수동 확인 ==="
```

### 2-3. Google Calendar 알림 설정 (Open Q-NEW 결정: 옵션 a 채택)

1인 운영자 기반에서 가장 경량한 알림 수단으로 **Google Calendar 반복 이벤트**를 채택한다.

```
이벤트명: [GoldPet] D-7 게이트 측정 — 전환 전 필수
날짜:     전환 예정일 7일 전
반복:     없음 (1회성)
설명:     RUNBOOK 섹션 2 참조. 게이트 G-1~G-5 모두 통과 시에만 전환 진행.
알림:     당일 09:00 KST 팝업 + 이메일
참석자:   jioabang@gmail.com + 백업 협력자 이메일
```

> **Phase 2 종료 후 재평가**: GitHub Actions cron (옵션 b) 또는 git pre-push hook (옵션 c)
> 도입 여부를 Phase 2 완료 시 재검토한다. 자세한 내용은 섹션 6 참조.

---

## 3. D-14 백업 협력자 준비

> 1인 운영자 SPOF를 완화하기 위해 전환 14일 전부터 백업 협력자를 준비시킨다.

### 3-1. 사전 준비 체크리스트 (D-14 완료 기준)

- [ ] **OAuth 테스트 계정 공유**
  - Kakao / Naver / Google / Apple 각 1개씩
  - 저장 위치: `1Password > GoldPet > OAuth Test Accounts` vault
    (대안: BitWarden Organization > `goldpet-test` Collection)
  - 공유 방법: vault 초대 이메일 → 협력자가 수락 확인
- [ ] **Admin 시드 계정 공유**
  - 계정: `e2eadmin@goldpet.com`
  - 저장 위치: `1Password > GoldPet > Admin Credentials`
  - Admin URL (dev): `https://admin.mannamsquare.com`
- [ ] **TestFlight 디바이스 접근 위임**
  - App Store Connect → TestFlight → Testers & Groups → 협력자 Apple ID 초대
  - 역할: Developer (빌드 설치 권한)
- [ ] **개발 서버 SSH 접근** (선택, 긴급 대응용)
  - `docs/reference_dev_server_ssh.md` 파일을 안전 채널로 공유
  - IP: 101.250.201.36

### 3-2. 백업 협력자 D-0 실행 체크리스트

운영자가 D-0 당일 부재 시 백업 협력자가 아래 항목을 순서대로 수행한다.

| 순서 | 항목 | 참조 문서 | 완료 기준 |
|------|------|---------|---------|
| 1 | `application-prod.yml` 소셜 로그인 키 교체 확인 | 섹션 4 타임라인 | 파일 수정 + 배포 완료 |
| 2 | M-AUTH-01 실행 (Kakao + Naver) | `docs/manual-test-checklist.md` | 2종 스크린샷 첨부 |
| 3 | M-PUSH-01 실행 | `docs/manual-test-checklist.md` | FCM 수신 로그 첨부 |
| 4 | 이상 발생 시 운영자에게 즉시 연락 후 롤백 | 섹션 4 롤백 절차 | — |

> **Apple 로그인 (백업 협력자 제외 가능)**:
> Apple private relay 이슈로 인해 Apple 로그인은 원칙적으로 운영자가 직접 수행한다.
> 부득이한 경우 섹션 5의 Apple 계정 가이드를 참조한다.

---

## 4. 회사 계정 전환 타임라인

### 4-1. 전략 3-tier

| Tier | 분류 | 예시 |
|------|------|------|
| **직렬 (권장)** | 의존성 있는 작업은 순서대로 | Firebase 신규 프로젝트 → APNs 키 → `google-services.json` 교체 |
| **병렬 (조건부)** | 독립적인 작업은 동시 진행 | Kakao 비즈앱 심사 신청 ‖ AWS prod 계정 생성 |
| **금지** | 동일 자원에 두 작업이 동시 접근 | prod DB migration + prod 배포 동시 실행 금지 |

### 4-2. 주차별 타임라인

#### D-21 (전환 3주 전)
- [ ] 백업 협력자 선정 및 역할 설명
- [ ] 1Password/BitWarden Organization vault 생성
- [ ] Google Play Console 법인 계정 신청 (신규 법인: 클로즈드 테스트 14일 의무)
- [ ] Apple Developer Program 법인 계정 신청 (D-U-N-S 보유 전제, 3~7일 소요)
- [ ] Kakao 비즈앱 심사 신청 (1~2주 소요 → **가장 먼저 시작**)

#### D-14 (전환 2주 전)
- [ ] OAuth 테스트 계정 협력자와 공유 완료 (3-1 체크리스트)
- [ ] Firebase 신규 프로젝트 `goldpet-prod` 생성
  - Android 앱 등록 (`com.mannam.goldpet`)
  - iOS 앱 등록 (`com.mannam.goldpet`)
  - `google-services.json` + `GoogleService-Info.plist` 발급
  - `firebase-service-account.json` 발급 → 안전 보관
- [ ] APNs Key (.p8) 발급 → Firebase Console 업로드

#### D-7 (전환 1주 전)
- [ ] **게이트 측정** (섹션 2 G-1~G-5 전체 확인)
- [ ] Google Calendar 알림 이벤트 생성 (섹션 2-3)
- [ ] AWS prod S3 버킷 `goldpet-prod` + IAM 사용자 생성
- [ ] Naver Developers 로그인 앱 회사 계정으로 신규 등록
- [ ] Naver Cloud Maps Client ID 발급 (Android/iOS 패키지 등록)
- [ ] `application-prod.yml` 키 교체 PR 작성 (머지는 D-0에)

#### D-0 (전환 당일)
1. `application-prod.yml` 소셜 로그인 키 PR 머지
2. `google-services.json` / `GoogleService-Info.plist` 교체 → 앱 빌드
3. Android release keystore 교체 (Google Play Console 서명 키 등록 포함)
4. Jenkins prod 배포 실행 (API → Frontend → Admin 순)
5. M-AUTH-01 전환 직후 실행 → evidence 첨부
6. M-PUSH-01 실행 → evidence 첨부
7. XF-08 (해당 시) 실행

#### D+1 (전환 다음날)
- [ ] M-AUTH-01 재실행 (4종 전수) → evidence 첨부
- [ ] M-PUSH-01 재실행 → evidence 첨부
- [ ] 개인 계정 OAuth 앱 비활성화 (Kakao/Naver 기존 앱 연결 끊기)
- [ ] 이상 없으면 D+3 내 Phase 5 개인 계정 정리 시작

### 4-3. 롤백 절차

| 상황 | 롤백 방법 |
|------|---------|
| M-AUTH-01 전환 후 실패 | `application-prod.yml` 이전 키 복원 → Jenkins 재배포 |
| Firebase 키 오류 | 이전 `google-services.json` 복원 → 앱 재빌드 |
| D-0 게이트 실패 | 전환 중단 → 1주 연기 → 섹션 2 게이트 재측정 |

---

## 5. OAuth Provider 테스트 계정 가이드

> 각 Provider별 테스트 계정 등록 방법과 회사 계정 전환 시 주의사항.

### 5-1. Kakao

| 항목 | 내용 |
|------|------|
| 테스트 계정 | 카카오 개인 계정 (비즈앱 미심사 기간에는 팀원 계정만 테스트 가능) |
| 비즈앱 심사 전 제한 | 앱 관리자/팀원으로 등록된 카카오 계정만 로그인 가능 |
| 권한 | Kakao Developers → 앱 → 팀원 관리 → 역할: 개발자 or 마케터 |
| Private Relay | 해당 없음 |
| 전환 후 주의 | 새 앱 Client ID로 교체 → 기존 사용자 재로그인 필요 (1회) |

**테스트 계정 등록 절차**:
1. `https://developers.kakao.com` → 내 애플리케이션 선택
2. 팀원 관리 → 팀원 추가 → 테스트 계정 카카오 이메일 입력
3. 역할: 개발자 설정 → 저장

### 5-2. Naver

| 항목 | 내용 |
|------|------|
| 테스트 계정 | 네이버 개인 계정 (검수 전에도 모든 계정 테스트 가능) |
| 권한 | 별도 팀원 등록 불필요 |
| 전환 후 주의 | Client ID/Secret 교체 후 기존 로그인 세션 유지됨 (토큰 재발급 불필요) |

### 5-3. Google

| 항목 | 내용 |
|------|------|
| 테스트 계정 | Google 일반 계정 |
| 권한 | Firebase Console → Authentication → 승인된 도메인 확인 |
| SHA-1/SHA-256 | Android release keystore 교체 시 Firebase Console에 새 지문 등록 필수 |
| 전환 후 주의 | OAuth Client ID 교체 시 기존 refresh token 무효화됨 → 재로그인 요구 |

**Google 서명 지문 등록 절차**:
```bash
# release keystore 지문 확인
keytool -list -v -keystore goldpet-release.jks -alias goldpet
# SHA-1 / SHA-256 → Firebase Console → 앱 설정 → SDK 설정 및 구성 → 디지털 지문 추가
```

### 5-4. Apple

| 항목 | 내용 |
|------|------|
| 테스트 계정 | Apple ID (2FA 활성화 필수) |
| Private Relay | 이메일 숨기기(private relay) 사용 계정은 테스트 제외 권장 |
| 전환 후 주의 | Service ID + .p8 Key 교체 시 기존 sub 값이 동일하게 유지되므로 재로그인 불필요 (단, Team ID 변경 시 sub 값 변경됨 → 재매핑 필요) |
| D-0 전 확인 | App Store Connect → Certificates → Sign in with Apple Key (AuthKey_XXXXXXXX.p8) |

**Apple private relay 처리 (현행 코드 반영)**:
- `SocialLoginService`: Apple private relay 이메일 (`@privaterelay.appleid.com`) 감지 시 `linkSuggestion` 스킵
- `application-prod.yml` → `auth.link_suggestion.enabled=false` 기본값 유지

---

## 6. Open Q-NEW 결정 기록

### Q-NEW-001: D-7 게이트 알림 수단 선택

**결정일**: 2026-05-25
**상태**: **확정 (옵션 a 채택)**

| 옵션 | 설명 | 채택 여부 |
|------|------|---------|
| **(a) Google Calendar 알림** | 전환 예정일 D-7에 Calendar 이벤트 + 이메일 알림 | **채택** |
| (b) GitHub Actions cron | 매주 스케줄 실행 + Slack/이메일 알림 | Phase 2 종료 후 재평가 |
| (c) git pre-push hook | push 시점에 게이트 체크 | Phase 2 종료 후 재평가 |

**채택 근거**:
- 1인 운영자 기반 → 가장 낮은 설정 비용
- GitHub Actions 워크플로 파일 추가 없이 즉시 운용 가능
- 알림을 협력자와 동시 공유 가능 (Calendar 초대)

**Phase 2 종료 시 재평가 기준**:
- smoke 자동화 10건 이상 완료된 경우
- Jenkins 또는 GitHub Actions CI 파이프라인 안정화된 경우
- 재평가 후 섹션 6에 Q-NEW-002로 기록

---

### Q-NEW-002: WALK-05 dispose guard 구현 시점

**결정일**: 미정
**상태**: **대기 (Phase 3 완료 후 결정)**

| 구현 방법 | 설명 |
|---------|------|
| Option A | `walk_screen.dart:dispose()`에 `if (_isWalking) await _stopWalk()` 가드 추가 |
| Option B | `dispose()`에서 `WalkRepository.saveIfActive()` 호출하여 서버 저장 보장 |

**배경**: `dispose()` 호출 경로 중 뒤로가기 확인 다이얼로그 우회 시나리오
(강제 업데이트, 세션 만료 리다이렉트, OS 백 제스처)에서 산책 데이터 미저장 위험 존재.

---

## 7. Phase별 진행 현황 추적

### 7-1. 전체 Phase 요약

| Phase | 이름 | 상태 | 완료 기준 |
|-------|------|------|---------|
| Phase 1 | 회사 명의 개발자 계정 개설 | 🔄 진행 중 | Apple/Google 법인 계정 승인 완료 |
| Phase 2 | 3rd-party 서비스 재발급 | ⏳ 대기 | Firebase/Kakao/Naver/AWS 전 항목 발급 완료 |
| Phase 3 | 앱 빌드 설정 교체 | ⏳ 대기 | keystore 교체 + `google-services.json` 교체 완료 |
| Phase 4 | 스토어 등록 + 심사 제출 | ⏳ 대기 | App Store + Play Console 심사 통과 |
| Phase 5 | 출시 후 개인 계정 정리 | ⏳ 대기 | 개인 OAuth 앱 비활성화 완료 |

### 7-2. Feature Test Phase 진행 현황

| Phase | 이름 | 상태 | Deliverable 수 | 대표 commit |
|-------|------|------|--------------|------------|
| Test Phase 1 | 자동화 인프라 + 핵심 도메인 | ✅ 완료 | 25+ commits | `34e29e8` |
| Test Phase 2 | Playwright E2E + Cross-flow IT | ✅ 완료 | 10 spec + 3 IT | `10907f0` |
| Test Phase 3 | P2 도메인 smoke + RUNBOOK | 🔄 진행 중 | 4 smoke + 문서 | — |

### 7-3. Test Phase 3 세부 항목

| Task | 담당 | 상태 | 파일 |
|------|------|------|------|
| D1: manual-test-checklist 보강 | worker-checklist-update | 🔄 진행 중 | `docs/manual-test-checklist.md` |
| D2: P2 도메인 smoke 4건 | worker-p2-smoke | ⏳ 대기 | `api/src/test/kotlin/.../smoke/` |
| D3: P2 단위 테스트 보강 | worker-p2-unit | ⏳ 대기 | `api/src/test/kotlin/.../` |
| D4: RUNBOOK 운영 룰 문서 | worker-runbook | 🔄 진행 중 | `docs/RUNBOOK.md` |

### 7-4. 미완 deliverable 추출 명령

```bash
# Feature Test Phase 1 관련 최근 2주 커밋 확인
git log --since="2 weeks ago" --oneline --grep="Phase 1\|phase1\|smoke\|IT\|E2E"

# smoke/IT/E2E 테스트 파일 목록
find api/src/test -name "*Smoke*" -o -name "*IT*" | sort
find frontend/tests -name "*.spec.ts" | sort
find admin/tests -name "*.spec.ts" | sort

# WALK-05 dispose 가드 구현 여부 확인
git log --oneline --grep="dispose.*walk\|walk.*dispose\|WALK-05"
```

### 7-5. 자동화 커버리지 현황 (2026-05-25 기준)

| 도메인 | Playwright | IT | Smoke P0 | Smoke P2 |
|--------|-----------|-----|---------|---------|
| Auth | ✅ | ✅ | ✅ | — |
| Walk | ✅ | — | ✅ | — |
| Chat | ✅ | ✅ | ✅ | — |
| Community | ✅ | — | ✅ | — |
| Friend | ✅ | — | ✅ | — |
| Notice | ✅ | — | ✅ | — |
| Gamification | — | — | — | ⏳ |
| Check-in | — | — | — | ⏳ |
| Health | — | — | — | ⏳ |
| Report | — | — | — | ⏳ |

> ⏳ = Test Phase 3 D2 작업 대상

---

*이 문서는 1인 운영자가 부재 시에도 백업 협력자가 운영을 이어갈 수 있도록 설계되었습니다.*
*갱신: Phase 완료 또는 의사결정 변경 시 즉시 반영.*
