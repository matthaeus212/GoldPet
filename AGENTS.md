# Repository Guidelines

## Project Structure & Module Organization

GoldPet is a hybrid mobile product with separate backend, web, admin, and Flutter shells.

- `api/`: Spring Boot Kotlin backend. Source is in `src/main/kotlin`, tests in `src/test/kotlin`, Flyway migrations in `src/main/resources/db/migration`, OpenAPI in `openapi.json`.
- `frontend/`: React/Vite user WebView app. Source is under `src`, assets under `public`, generated API types in `src/api/schema.d.ts`, output in `dist`.
- `admin/`: React/Vite admin console with the same generated API type pattern.
- `app/`: Flutter iOS/Android hybrid shell. Dart source is in `lib`, assets in `assets`, platform projects in `ios` and `android`.
- `docs/`: product, runbook, and implementation specs.

## Build, Test, and Development Commands

- Backend: `cd api && ./gradlew test` runs JUnit tests. Use `--tests 'package.ClassName' --rerun-tasks` for targeted verification.
- API spec: `cd api && ./gradlew generateOpenApiDocs`; then run `npm run generate:api` in `frontend/` and `admin/`.
- Frontend: `cd frontend && npm run dev`; `npm run build` runs TypeScript and Vite; `npm run test:unit` runs Vitest; `npm run test:e2e` runs Playwright.
- Admin: `cd admin && npm run dev`, `npm run build`, `npm run test:unit`.
- Flutter: `cd app && flutter analyze` checks Dart; use `flutter test` when tests are present.

## Coding Style & Naming Conventions

Kotlin uses Spring Boot conventions, nullable types explicitly, and Flyway migrations named `VNN__Description.sql`. TypeScript is strict React with generated OpenAPI types as the contract source; keep API overrides narrow in `frontend/src/types/api.ts`. Flutter follows `flutter_lints`; keep bridge code defensive for rolling backend/client deploys. Do not hand-edit generated API schemas except as part of regeneration.

## Testing Guidelines

Add focused tests for changed behavior. Backend tests use JUnit 5 and Mockito Kotlin; readable backtick test names are common. Frontend unit tests use Vitest, and browser flows use Playwright. For auth, onboarding, payments, privacy, or native bridge changes, cover success and edge cases.

## Commit & Pull Request Guidelines

Recent history uses Conventional Commit style, for example `feat(auth): ...`, `fix(ios): ...`, and `docs(spec): ...`. Keep commits scoped. PRs should include the user-visible change, linked issue or context, verification commands, migration/API type notes, and screenshots for UI changes.

## Security & Configuration Tips

Never commit secrets, DSNs, private keys, or raw production data. Treat PII carefully: prefer explicit state fields over inferring workflow state from `name`, `phoneNumber`, `birthDate`, or `gender`. For App Store-sensitive changes, scan built bundles for forbidden third-party store references.

## 하네스: 종합 코드 리뷰

**목표:** 변경(diff/PR/경로)을 아키텍처·보안·성능·코드스타일 4개 차원으로 병렬 감사하고, 외부 독립 AI(codex/gemini) 리뷰를 더해 하나의 리포트로 통합한다.

**트리거:** 코드 리뷰·종합 리뷰·아키텍처/보안/성능/스타일 점검·PR 리뷰·변경 감사 요청 시 `.agents/skills/code-review-team/SKILL.md` 절차를 따른다. 기본 대상은 현재 브랜치 diff(vs main), 인자로 경로/PR/커밋범위 override.

**Codex 오케스트레이션 어댑터:** `TeamCreate` 등 팀 도구가 없으므로 `.codex/agents/*.toml` subagents를 병렬 spawn하거나 `codex exec --sandbox read-only` subprocess로 4 감사 + 외부 리뷰를 돌리고, `_workspace/` 파일로 데이터를 전달한 뒤 review-synthesizer로 통합한다. 상세: `.agents/skills/code-review-team/references/codex-adapter.md`.

**하네스를 만들거나 고치려면** `skills/myharness/SKILL.md`(myharness 메타 스킬)를 따른다. `.claude/`(Claude Code)와 `.codex/`·`.agents/`(Codex)는 같은 정본을 가리킨다 — 한쪽만 갱신하면 drift.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-06-19 | 초기 구성 (팬아웃/팬인 팀 5에이전트 + 외부리뷰 + 종합) | 전체 | - |
| 2026-06-19 | 외부리뷰 실행기 번들 스크립트화 + 우아한 저하 | skills/external-review (run-external-review.sh) | 실행 중 gemini free-tier 미지원(IneligibleTierError) + timeout 깨진 심링크 실패 → timeout 실행검증·도구 헬스체크·codex 단독 저하 모드 내장 |
