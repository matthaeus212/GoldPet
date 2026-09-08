# Walk Photo Presigned URL — Observability Reference

Plan: `.omc/plans/walk-photo-feed-arch.md` §4.4  
Related runbook: `deploy-dev/runbooks/walk-photo-presigned-url.md`

---

## Backend Metrics (Micrometer → `/actuator/prometheus`)

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `walk_photo_presigned_served_total` | Counter | `bucket=private` | Presigned URL emitted in a feed response (`toWalkPhotoResponse`) |
| `walk_photo_hidden_presigned_served_total` | Counter | — | Presigned URL emitted for an owner-visible hidden photo (should be zero for non-owners) |
| `walk_photo_mint_endpoint_total` | Counter | `result=ok\|denied\|not_found` | Outcome of each call to `GET /api/v1/walks/photos/{spotId}/url` |
| `walk_photo_presigned_feature_flag_state` | Gauge | — | `1` if `WALK_PHOTO_PRESIGNED_URL_ENABLED` is on, `0` if off — polled per Prometheus scrape via `SystemSettingService` |

### Actuator endpoint

`/actuator/prometheus` — exposed in `application.yml` (`management.endpoints.web.exposure.include`).  
`SecurityConfig` permits all `/actuator/**` without authentication.

---

## Frontend Client Metrics (sendBeacon → `/api/v1/telemetry/client-error`)

These are emitted by the browser and recorded server-side. They do not appear in `/actuator/prometheus`.

| Metric name (error code) | When emitted | Key fields |
|---|---|---|
| `WALK_PHOTO_IMG_LOAD_FAIL` | `<img onError>` fires on a walk photo | `{ status, photoId, urlExpiryEstimate, srcHasAmzSignature: boolean }` |
| `walk_photo_canvas_taint_error_total` | `canvas.toBlob()` throws `SecurityError` in `composeImage.ts` | Indicates S3 CORS misconfiguration — share flow cannot composite watermark |
| `walk_photo_cors_error_total` | `<img onError>` with no HTTP response (`net::ERR_FAILED`) | `{ reason: "err_failed\|err_blocked\|unknown" }` |

---

## Alerting Rules

### 1. Presigned URL 403 rate (S3 access logs → CloudWatch → Prometheus)

```
# Ratio alert — expired URL storm
alert: WalkPhotoPresigned403RatioHigh
expr: >
  rate(walk_photo_presigned_403_total[5m])
  / rate(walk_photo_presigned_served_total[5m]) > 0.001
for: 5m
annotations:
  summary: "Presigned URL 403 rate exceeds 0.1% — possible expiry storm or clock-skew event"

# Absolute alert — sudden outage or signature bug
alert: WalkPhotoPresigned403AbsoluteHigh
expr: rate(walk_photo_presigned_403_total[1m]) > 10
for: 1m
annotations:
  summary: "More than 10 presigned-URL 403s/min — S3 outage or signature clock bug"
```

### 2. Hidden-photo URL emission (regression detection)

```
alert: WalkPhotoHiddenPresignedServed
expr: rate(walk_photo_hidden_presigned_served_total[5m]) > 0
for: 0m
severity: critical
annotations:
  summary: "Presigned URLs being minted for hidden photos — verify authorization predicate has not regressed"
```

Note: This counter fires for **owner** paths (owner can see their own hidden photos). A non-zero value is expected during flag-on operation. The alert is most useful during the first 24 h of rollout when `walk_photo_presigned_feature_flag_state = 1` — baseline the rate against `walk_photo_presigned_served_total` to detect abnormally high ratios.

### 3. Feature flag state drift (cross-pod consistency)

```
alert: WalkPhotoFlagStateMismatch
expr: >
  count(walk_photo_presigned_feature_flag_state == 1)
  != count(walk_photo_presigned_feature_flag_state == 0)
  and ignoring(instance) (
    count(walk_photo_presigned_feature_flag_state) > 1
  )
for: 2m
annotations:
  summary: "Flag WALK_PHOTO_PRESIGNED_URL_ENABLED differs across pods — hot-switch may not have propagated. Check Redis cache or restart affected pods."
```

### 4. Canvas taint / CORS error (client-side, threshold 0)

Monitor `walk_photo_canvas_taint_error_total` via the client-error telemetry pipeline.  
Any non-zero value in production indicates S3 bucket CORS is misconfigured (missing `Access-Control-Allow-Origin` or missing `crossorigin="anonymous"` on the `<img>` element). The share-flow watermark composite will fail for all users until resolved.

---

## Thresholds Summary

| Alert | Threshold | Window | Severity |
|-------|-----------|--------|----------|
| 403 ratio | > 0.1% | 5 min | warning |
| 403 absolute | > 10/min | 1 min | critical |
| Hidden presigned | > 0/min | 0 min | critical |
| Flag state mismatch | any pod differs | 2 min | warning |
| Canvas taint | > 0 | — | critical |

---

## Deprecation Telemetry

`legacy_photo_url_cache_calls_total{app_version}` — emitted by frontend `photoUrlCache.ts` shim.  
Increment is a no-op once `imageUrl` is always an HTTPS URL (flag-on + new frontend), but will be non-zero from stale bundles or old app versions routing through the proxy path.

**Shim removal gate:** When `rate(legacy_photo_url_cache_calls_total{app_version=~"<target_version>.*"}[24h]) == 0` for 7 consecutive days, the shim can be removed (release N+2 schedule per §3.3).
