# Runbook: Walk Photo Presigned URL Rollout

**Feature:** Replace blob-proxy walk photo serving with S3 presigned HTTPS URLs  
**Plan:** `.omc/plans/walk-photo-feed-arch.md` §3.8  
**Flag:** `WALK_PHOTO_PRESIGNED_URL_ENABLED` (default `false`)  
**Rollback:** Hot-switch via admin console — no redeploy required

---

## Pre-flight Checklist (Day 0, before any deploy)

### 1. Legacy key audit (ship-gated — must be zero)

Run against **production** DB before merging the backend PR:

```sql
SELECT COUNT(*)
FROM walk_spots
WHERE image_url IS NOT NULL
  AND image_url !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\.[a-zA-Z0-9]+)?$';
```

**Expected result: 0**  
If non-zero: investigate legacy keys before deploying. Do not merge until resolved.

### 2. Environment variable check

Verify `S3_PUBLIC_ENDPOINT` is set correctly per environment before the backend deploy:

| Env   | `S3_PUBLIC_ENDPOINT` value                     |
|-------|------------------------------------------------|
| local | `http://192.168.0.87:9100` (LAN IP for devices)|
| dev   | `https://minio.mannamsquare.com` (HTTPS via nginx) |
| prod  | AWS S3 direct HTTPS (`https://s3.ap-northeast-2.amazonaws.com`) |

### 3. MinIO SigV4 smoke test (local / dev)

After starting the API, verify presigned URL signing is consistent with the public endpoint:

```bash
# 1. Get a presigned URL (flag must be temporarily on in local/dev only)
TOKEN="<JWT from login>"
PRESIGNED=$(curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/api/v1/walks/me/photos | jq -r '.content[0].imageUrl')

echo "$PRESIGNED"   # must start with http://192.168.0.87:9100 (local) or https://... (dev/prod)

# 2. Fetch the URL directly — must return 200
curl -v "$PRESIGNED" -o /dev/null
```

**Expected:** HTTP 200. If 403 `SignatureDoesNotMatch` → `S3_ENDPOINT` ≠ `S3_PUBLIC_ENDPOINT` at signing time; check `FileService.getPresignedUrl()`.

---

## Staged Deploy Order

### Day 1 — Backend deploy to dev

```bash
# Deploy via Jenkins or script
./deploy-dev/scripts/deploy-api.sh
# OR Jenkins: API job, PROFILE=dev
```

**Smoke test** (feature flag stays `false`):
- `GET /api/v1/walks/me/photos` → `imageUrl` is still a raw S3 key (flag off)
- `imageKey` field now present in response (new additive field)
- Existing frontend gallery still loads images via `/files/{key}/content`

### Day 2 — Apply S3 CORS to dev MinIO

```bash
# On dev server (SSH in first):
cd /path/to/GoldPet
ENV=dev MINIO_ACCESS_KEY=<key> MINIO_SECRET_KEY=<secret> \
  ./deploy-dev/s3/apply-cors.sh
```

**Verify CORS headers:**

```bash
curl -v -H "Origin: https://app.mannamsquare.com" \
  -H "Access-Control-Request-Method: GET" \
  -X OPTIONS \
  "https://minio.mannamsquare.com/goldpet-private/<any-key>" 2>&1 | grep -i "access-control"
```

**Expected headers in response:**
```
Access-Control-Allow-Origin: https://app.mannamsquare.com
Access-Control-Allow-Methods: GET, HEAD
Access-Control-Expose-Headers: ETag, Content-Length, Content-Range, Accept-Ranges
```

**Canvas taint test** (Playwright — run after CORS is applied):
```bash
cd frontend
npx playwright test walk-gallery-share.spec.ts
```
Green = CORS correct. Red = do not proceed to Day 3.

### Day 3 — Enable flag on dev + frontend deploy

```bash
# 1. Enable feature flag via admin console
# Admin → System Settings → WALK_PHOTO_PRESIGNED_URL_ENABLED → "true"
# (uses SystemSettingService.setValue() — Redis-backed, propagates to all pods)

# 2. Verify flag took effect on all pods (check Prometheus gauge)
# walk_photo_presigned_feature_flag_state should be 1 on all instances

# 3. Deploy frontend
./deploy-dev/scripts/deploy-frontend.sh
# OR Jenkins: Frontend job, ENV=dev
```

**iOS + Android sweep:**
- Device matrix: iPhone SE 2020, iPhone 12, iPhone 15 Pro, Pixel 5a, Galaxy A32
- Test: 500 photos, swipe at ~1/sec for 2 min
- Expected: no freeze, RSS < 300 MB (Xcode Instruments / Android Profiler)
- Expected: no `URL.createObjectURL` calls in browser devtools network tab

**Share flow test:**
- Open gallery → idle 5 min (let URL age) → tap share → image composes successfully
- Trigger stale URL path: open gallery → wait 31 min → tap share → expect auto-recovery toast if mint fails

### Day 4 — Backend deploy to prod (flag=false)

```bash
# Jenkins: API job, PROFILE=prod
```

- Flag defaults to `false` → no behaviour change for prod users
- Verify `imageKey` field present in prod API responses
- Old frontend binaries: unaffected (they receive raw key in `imageUrl` as before)

### Day 5 — Apply S3 CORS to prod + 10% canary

```bash
# 1. Apply CORS to prod S3 bucket
ENV=prod AWS_REGION=ap-northeast-2 ./deploy-dev/s3/apply-cors.sh

# 2. Flip flag for 10% canary via admin console
# (manually route 10% of users or use user_id % 10 == 0 logic in SystemSetting)
```

**Monitor for 24 hours:**

| Metric | Alert threshold |
|--------|----------------|
| `walk_photo_presigned_403_total` / `walk_photo_served_total` | > 0.1% |
| `walk_photo_presigned_403_total` rate | > 10/min absolute |
| `walk_photo_presigned_feature_flag_state` | < 1 on any pod |
| `walk_photo_canvas_taint_error_total` | > 0 |
| `walk_photo_hidden_presigned_served_total` | > 0 (alert immediately) |

### Day 6 — Full rollout (100%) + frontend prod deploy

Only proceed if Day 5 metrics are clean.

```bash
# 1. Flip flag to 100% via admin console
# Admin → System Settings → WALK_PHOTO_PRESIGNED_URL_ENABLED → "true"

# 2. Deploy frontend to prod
# Jenkins: Frontend job, ENV=prod
```

### Day 7+ — Soak and cleanup schedule

- **Week 1:** Monitor metrics daily. No code changes.
- **Week 2:** If metrics clean, schedule `photoUrlCache.ts` shim deprecation (release N+2).
  - Gate: `legacy_photo_url_cache_calls_total{app_version}` shows zero calls from app versions ≥ this release.
- **Week 4:** Consider removing the feature flag call in `signedUrlOrNull` (hot-path cleanup).
- **Sprint N+1:** Land Option C (private-bucket `_medium` variants) as a separate work item.

---

## Rollback Procedure

### Immediate rollback (no redeploy, < 60 seconds)

```
Admin Console → System Settings → WALK_PHOTO_PRESIGNED_URL_ENABLED → "false"
```

Effect:
- `signedUrlOrNull()` returns `null` → `imageUrl` falls back to raw S3 key
- Frontend `resolvePhotoUrl` shim detects non-`http` input → routes through `/files/{key}/content`
- Gallery, share, and viewer all work via the old proxy path
- In-flight presigned URLs remain valid until their TTL (up to 30 min), then expire

**Confirm rollback took effect:**
```bash
# Prometheus: walk_photo_presigned_feature_flag_state should drop to 0 within 60s on all pods
# Or: GET /api/v1/walks/me/photos and verify imageUrl is a raw key (no X-Amz-Signature in value)
```

### Hidden-photo leak emergency

If `walk_photo_hidden_presigned_served_total > 0` fires in prod:

1. **Immediately** flip flag to `false` (see above). New URLs stop being minted.
2. Existing in-flight URLs expire within 5 min (hidden photos use 5-min TTL per §3.2).
3. If needed: admin can rotate affected S3 keys via admin dashboard (copy object → new key → delete old).
   This invalidates any exfiltrated links before TTL.
4. File incident report; investigate how hidden photos bypassed `requiresMint=true` suppression.

---

## Key Files

| File | Purpose |
|------|---------|
| `deploy-dev/s3/goldpet-private-cors-policy.json` | S3/MinIO CORS policy (exact JSON to apply) |
| `deploy-dev/s3/apply-cors.sh` | Script to apply CORS (local/dev/prod) |
| `api/…/walk/service/WalkService.kt` | `signedUrlOrNull()` + feature flag |
| `frontend/src/features/walk/utils/photoUrlCache.ts` | Compatibility shim (retain 2 releases) |
| `.omc/plans/walk-photo-feed-arch.md` | Full architectural plan (ADR, options, tests) |
