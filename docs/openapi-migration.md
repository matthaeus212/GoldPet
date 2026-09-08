# OpenAPI Type Generation — Architecture & Workflow

This document captures the post-Phase-4 state of the OpenAPI-driven contract
pipeline. It is the authoritative reference for adding/changing API surface area
in this repository.

## Why

Pre-migration, every backend DTO had a hand-written TypeScript twin in
`frontend/src/services/*` and `admin/src/services/*`. Drift between the two
caused user-visible bugs (`AnalysisStatus` enum value missing in TS — c2eb8ca,
`AuthResponse.phoneNumber` typo — b16d8e0). The cure: a single source of truth.

> **Source of truth = backend Kotlin DTO + Springdoc-generated `openapi.json`.
> All TS types for API responses are *derived*; no hand-written duplicates of
> generated DTOs.**

## Pipeline at a glance

```
┌──────────────────┐      ┌─────────────────────┐      ┌────────────────────────┐
│ Kotlin DTOs      │      │ api/openapi.json    │      │ src/api/schema.d.ts    │
│ + Springdoc      │ ───▶ │ (generated, sha256  │ ───▶ │ (openapi-typescript    │
│ @Operation/@Tag  │      │  pinned in CI)      │      │  in frontend + admin)  │
└──────────────────┘      └─────────────────────┘      └────────────────────────┘
                                                                 │
                                                                 ▼
                                                       ┌────────────────────────┐
                                                       │ src/types/api.ts       │
                                                       │ Schemas[...] re-export │
                                                       │ + OpenEnum<T>          │
                                                       └────────────────────────┘
                                                                 │
                                                                 ▼
                                                       ┌────────────────────────┐
                                                       │ services/*.ts          │
                                                       │ alias-only DTOs +      │
                                                       │ apiClient calls        │
                                                       └────────────────────────┘
```

## Backend: spec generation

### Codegen profile

`api/src/main/resources/application-codegen.yml` provides stub values for the
sentinel-guarded beans (`EncryptionConfig`, OAuth2 client registry, MinIO,
Firebase). This lets Springdoc boot the application on a clean Jenkins agent
with no project secrets.

```bash
cd api
./gradlew generateOpenApiDocs   # writes api/openapi.json
```

The Gradle task normalizes the output via
`jq -S 'walk(if type == "array" then sort_by(tostring) else . end)'` so that
JVM minor-version array ordering quirks don't produce spurious diffs.

### Drift gate

`./gradlew verifyOpenApiSpec` regenerates the spec into a temp file and
compares the sha256 against the committed `api/openapi.json`. Non-zero exit on
drift. CI runs this on every backend PR.

### Annotation baseline

Every business controller carries `@Tag` + `@Operation` annotations so Springdoc
emits clean `operationId` values and grouped tags. Internal-only controllers
(error handlers, root probes, infrastructure callbacks) are marked with
`@OpenApiInternal` and excluded from the gap audit.

## Frontend / Admin: type consumption

### Generation

```bash
cd frontend && npm run generate:api   # → frontend/src/api/schema.d.ts
cd admin    && npm run generate:api   # → admin/src/api/schema.d.ts
```

Both call `openapi-typescript ../api/openapi.json -o src/api/schema.d.ts`. The
generated file is committed (so PRs touching the API surface make the diff
visible to reviewers).

### Re-export layer (`src/types/api.ts`)

All consumer code imports from `src/types/api.ts`, which re-exports
`Schemas[...]` slices under stable local names and adds two helpers:

- `Schemas` — alias for `components['schemas']` from the generated file.
- `OpenEnum<T>` — `T | (string & {})` open-union wrapper. Use it for enums on
  rolling-deploy paths so unknown values from a newer backend don't become a
  TypeScript build break.

Every Phase 3.x commit added one section to this file. Domain enums are
exposed as `Schemas['ResponseDto']['enumField']` so they stay derived.

### typedClient

`src/services/typedClient.ts` wraps the underlying axios `apiClient` and binds
the request to a `keyof paths` template literal so HTTP method + path mistakes
fail at compile time. Adoption is per-domain — see follow-up below.

## Drift detection — three layers

| Layer        | Tool                                       | Catches                                                   |
| ------------ | ------------------------------------------ | --------------------------------------------------------- |
| Compile time | `tsc` against `schema.d.ts`                | DTO shape, field name, field type, enum membership        |
| Compile time | `typedClient` + `keyof paths`              | HTTP path / method drift                                  |
| Build time   | `./gradlew verifyOpenApiSpec`              | Backend changed without regenerating spec                 |
| Build time   | `npm run lint:contract`                    | Hand-written domain enum union sneaks back into a service |
| Runtime      | Zod validators on 5 high-risk DTOs         | Jackson serializer quirks not encoded in the spec         |
| Runtime      | `ClientLogController` `contract-drift` tag | Production logs of unknown enum values, shape mismatch    |

The runtime layer (`POST /api/v1/client-logs/error` with
`extra.tag = "contract-drift"`) is the observability backstop. It hits the
backend logs within seconds and is the canary for any drift the static layers
miss.

## Adding a new endpoint or DTO

1. **Backend.** Add the controller method with `@Operation(summary = "...")`.
   Use a real Kotlin `data class` (not `Map<String, Any>` or
   `ResponseEntity<Any>`) so Springdoc can derive the schema.
2. Run `./gradlew generateOpenApiDocs`.
3. Verify the new schema appears: `grep '"NewDtoName"' api/openapi.json`.
4. **Frontend / Admin.** `npm run generate:api` in both projects.
5. Add the re-export to `src/types/api.ts`.
6. In the service file, import the alias and use it in the function signature
   (and in the `typedClient` call once that domain is migrated).
7. `npm run typecheck && npm run build:dev && npm run lint:contract`.
8. Commit backend + spec + both frontends in one PR.

## Renaming a backend field — migration path

If a DTO field rename ships, the old client may still send the legacy name for
one release window. Apply `@field:JsonAlias("oldName")` on the renamed Kotlin
property; the alias accepts the old name on inbound and the new name goes out.
Remove the alias one release after access logs confirm zero hits on the legacy
name.

Reference: Phase 3.5 (`CommunityPostResponse.userId → authorId`).

## Follow-ups — shipped

All Phase 4 follow-ups listed previously are now landed on `main`:

- **`WalkController.getWalkRanking` split** — `c6580c0`. `/walks/ranking`
  (weekly|monthly) + `/walks/ranking/calendar` both expose typed responses
  (`WalkRankingResponse`, `WalkCoupleRankingResponse`) via Springdoc.
- **`Species` DTO extension** — `0d2cd8f`. `SpeciesResponse` now carries
  `description` + `breedCount`; the manual `Species` interface is gone.
- **Phase 4 typedClient rollout** — `ee46343` (PoC) → `cd078a2` → `00d5cf7`
  (frontend 22/22) → `0286c90` (admin 22/22). All JSON endpoints go through
  `typedClient`; `apiClient` stays only for multipart, blob, DELETE-with-body,
  and endpoints marked `@OpenApiInternal` or not in the spec.
- **Contract test wiring** — `3f4b7bb`. Jenkins `OpenAPI Contract Test (dev)`
  stage runs `frontend/e2e/api-contract.spec.ts` against `api.mannamsquare.com`
  on dev builds, marks the build `UNSTABLE` on drift, does not block deploy.

## Still open

- **`@JsonAlias("userId")` removal** (see `.omc/plans/open-questions.md`) —
  waits one release past the `CommunityPostDto.userId → authorId` rename
  ship so legacy clients stop sending the old name. Schedule removal after
  access logs confirm zero hits on `userId`.
- **operationId strategy** — reactive decision; revisit the first time an
  operationId churn breaks the generated TS (Pre-mortem Scenario 5).
- **Admin husky adoption** — revisit 90d post-migration if admin contract-drift
  incidents > 0.

## Repository touchpoints

- `api/build.gradle.kts` — `springdoc-openapi-gradle-plugin` block,
  `generateOpenApiDocs` finalizer, `verifyOpenApiSpec` task.
- `api/src/main/resources/application-codegen.yml` — codegen profile stubs.
- `api/openapi.json` — committed spec (sha256-pinned).
- `frontend/src/api/schema.d.ts`, `admin/src/api/schema.d.ts` — generated.
- `frontend/src/types/api.ts`, `admin/src/types/api.ts` — re-export layer.
- `frontend/src/services/typedClient.ts` — strongly-typed axios wrapper.
- `frontend/build-scripts/check-no-literal-enums.sh` — Phase 4.2 lint.
- `.omc/plans/openapi-typegen-migration.md` — full historical plan.
- `.omc/plans/open-questions.md` — open follow-ups.
