# Design: `signupCompletedAt` — Onboarding-Completion Flag

**Date:** 2026-06-16
**Status:** Approved (pending spec review)
**Author:** Hong SungChul (with Claude)

## Problem

Signup-completion is currently inferred from the presence of a PII profile field, and the
two clients disagree on *which* field:

| Client | DTO consumed | Completion criterion | Correct? |
|--------|--------------|----------------------|----------|
| Flutter `native_bridge.dart:108` | `AuthResponse.UserInfo` | `name != null` | ✅ |
| Web `LoginSuccessPage.tsx:26,57` | `UserResponse` (`/api/v1/users/me`) | `nickname != null` | ❌ bug |

`nickname` is auto-filled from the OAuth profile on social **first-touch**
(`SocialLoginService.kt:291`), so a brand-new OAuth user who has *not* completed the
SNS additional-signup screen is mis-judged as "completed" on the web flow — they skip
onboarding.

The deeper issue: tying completion to PII fields permanently entangles product logic with
Apple's review stance on which fields are mandatory. Demographics (`gender`/`birthDate`/
`phoneNumber`) are already optional per Apple 5.1.1(v); `name` may follow. Completion must
be a standalone **onboarding-state** fact.

## Solution

Introduce `User.signupCompletedAt: LocalDateTime?` as the **single source of truth** for
onboarding completion, fully decoupled from every PII field. Expose it to clients as a
derived **boolean** `signupCompleted` (the raw timestamp stays server-side — least-leaky).
Both clients adopt `signupCompleted` as the sole criterion, in two waves matched to their
release vehicles: **server + web converge in this deploy; Flutter native converges in the
next app build** (backward-compatible until then — see Rollout).

Apple narrative stays clean: *"성별/생년월일/전화번호는 선택이며, 가입 완료 여부는 별도
온보딩 완료 상태(`signupCompletedAt`)로 관리한다."*

## Field semantics (target end-state)

- `nickname` — service display name (auto-filled on OAuth first-touch; **not** a completion signal)
- `name` — real-name profile value; long-term candidate to make optional
- `phoneNumber`, `birthDate`, `gender` — optional profile values
- `signupCompletedAt` — **the** onboarding/additional-signup completion criterion

## Changes

### 1. DB migration — `V84__Add_signup_completed_at.sql`

```sql
ALTER TABLE users ADD COLUMN signup_completed_at TIMESTAMP NULL;

-- Conservative backfill: preserve existing-account login (never lock out a real user),
-- but never mark a nickname-only social first-touch row as complete.
UPDATE users
SET signup_completed_at = COALESCE(created_at, updated_at, CURRENT_TIMESTAMP)
WHERE oauth_provider = 'LOCAL'   -- LOCAL members: complete by definition
   OR password IS NOT NULL       -- belt-and-suspenders for LOCAL
   OR name IS NOT NULL           -- social who finished snsSignup (name set there)
   OR username IS NOT NULL;      -- preserves any legacy username-bearing rows
-- nickname-only social rows stay NULL → correctly "incomplete".
```

Notes:
- `created_at` is a JPA-auditing field (`BaseTimeEntity.createdAt`); existing rows are
  expected to carry it. The `lateinit` declaration alone does not prove a DB-level NOT NULL
  constraint, so `COALESCE(created_at, updated_at, CURRENT_TIMESTAMP)` is kept as defensive
  null-safety regardless of the actual column constraint.
- `name` / `phone_number` are AES-256-GCM encrypted via `EncryptionConverter`. The backfill
  only **null-checks the ciphertext column** (never compares decrypted values), which is the
  intended semantics — a row has a name iff the encrypted column is non-null.
- Follows the project's Flyway out-of-order rules; plain `ALTER`/`UPDATE`, no
  `CONCURRENTLY`, runs in a transaction.

### 2. Entity — `User.kt`

```kotlin
@Column(name = "signup_completed_at")
var signupCompletedAt: LocalDateTime? = null
```

### 3. Completion write points (the only two)

- `AuthService.signup()` (LOCAL): set `signupCompletedAt = LocalDateTime.now()` at `User(...)`
  creation — LOCAL signup is complete immediately.
- `AuthService.snsSignup()`: `user.signupCompletedAt = user.signupCompletedAt ?: LocalDateTime.now()`
  before save — idempotent, stamps the first completion only.
- `SocialLoginService` first-touch user creation: leave `null` (already does) → correctly incomplete.

### 4. DTO exposure — derived boolean

**`AuthResponse.UserInfo`** (`AuthDto.kt`): add `val signupCompleted: Boolean = false`.
Set `signupCompleted = user.signupCompletedAt != null` at every **real-user** construction site:

- `AuthService.kt`: `signup` (104), `login` (132), `snsSignup` (183), `devLogin` (263)
- `SocialLoginService.kt`: first-touch new-user (326), existing-user logins (384, 461)
- `AuthController.kt`: **refresh-token rotation response (191)** — builds `UserInfo` manually; must include the field
- Link-suggestion **stub** sites (`SocialLoginService.kt:245, 271`, `id = 0` empty user): keep default `false` ✅

**`UserResponse`** (`UserResponse.kt`): add `val signupCompleted: Boolean? = null` (nullable).
- `from()` (the `/api/v1/users/me` self payload the web flow reads): derive as
  `user.signupCompletedAt != null` — a real boolean, same meaning as `UserInfo`.
- `publicFrom()` (other users' public profiles): set `null`, **not** `false`. A stranger's
  onboarding state is not part of the public-profile contract (`publicFrom` already hides
  `name`, `birthDate`, `phoneNumber`, `goldBalance`). `null` honestly signals "not applicable
  / not exposed" rather than a misleading `false` that reads as a real state to API consumers.
  The web self-flow only consumes `from()`, where the value is always a concrete boolean, so
  the nullability never affects completion judgment.

### 5. Clients — converge on `signupCompleted`

**Web `LoginSuccessPage.tsx`** (single-deploy cutover): replace `!user.nickname` at lines
26 and 57 with `!user.signupCompleted`. Because backend + web ship in one deploy, the web
goes straight to the flag — no throwaway intermediate "temp-fix to `name`" needed (that only
mattered across separate deploys), and the web is never left mis-judging.

**Flutter `native_bridge.dart`** (`~line 108`): flag-first with `name` fallback for
old-server / cache / staging combinations:

```dart
final signupCompleted =
    user['signupCompleted'] as bool? ??
    ((user['name'] as String?)?.isNotEmpty == true);
final hasCompletedSignup = signupCompleted;
```

New server → explicit flag is authoritative. Old server / stale cache → falls back to the
existing `name` behavior, so already-completed users are never regressed to `false`. This
native change only affects the **next app build**; the in-review iOS 1.0.0 build keeps
reading `name` (backend continues to populate it) — fully backward compatible.

**Frontend types:** add `signupCompleted?: boolean` to `authStore.ts` `User` interface
(line 23); regenerate OpenAPI TypeScript types so the field is present in generated schema.

## Testing

- **Migration/backfill:** LOCAL rows → flag set; social-with-name → set; nickname-only social
  → stays NULL; null-`created_at` edge handled by `COALESCE`.
- **Service:** `signup()` stamps the flag; `snsSignup()` stamps the flag on completion and
  **preserves an already-set `signupCompletedAt`** (timestamp unchanged if the user re-runs
  the screen — note `snsSignup` still overwrites `nickname`/`name`, so this is timestamp
  stability only, not full-API idempotency); first-touch social login leaves it null.
- **DTO:** `UserInfo.signupCompleted` and `UserResponse.from().signupCompleted` reflect
  `signupCompletedAt != null`; `UserResponse.publicFrom().signupCompleted` is `null`; the
  refresh-token path includes the field; link-suggestion stub sites return `false`.
- **Regression:** update existing tests asserting `UserInfo` / `UserResponse` shape.

## Out of scope (noted follow-ups)

- **Self/public DTO split.** Long-term, `UserResponse` should be split into a self-profile
  DTO and a public-profile DTO so self-only fields (`signupCompleted`, `email`, `birthYear`,
  precise location, `goldBalance`, `name`…) don't live on the shared public contract at all.
  This change uses the minimal honest stopgap (`Boolean?`, `null` in `publicFrom`) and defers
  the split as a dedicated refactor.
- Making `name` an optional field (separate long-term product decision).
- Migrating the in-review iOS build (backward-compatible by design).
- `PasswordExpiryPage` / `AutoLogoutPage` backend triggers (unrelated).

## Rollout

Single atomic deploy: API (migration + entity + DTOs) + frontend web (LoginSuccessPage +
types). Flutter `native_bridge` change lands in the next app build, backward-compatible with
the currently-shipped/in-review build.
