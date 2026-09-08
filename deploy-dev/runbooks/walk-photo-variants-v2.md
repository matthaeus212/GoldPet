# Walk-Photo Variants v2 Deploy Runbook

## Prerequisites
- [ ] Pre-flight SQL audit: `SELECT url, COUNT(*) FROM file_attachments GROUP BY url HAVING COUNT(*) > 1;` — expected rows: 0. If non-zero, decide dedupe strategy before migration.
- [ ] V52 migration tested in dev environment
- [ ] Backend + frontend deployed to dev, smoke tested

## Phase 1: Deploy code (flag OFF)
1. Merge PR #2 (backend + frontend)
2. Deploy api + frontend to prod
3. Verify `WALK_PHOTO_VARIANTS_ENABLED=false` (default)
4. Verify existing walks still render (using original presigned URL)

## Phase 2: Backfill
1. Kick off backfill via admin endpoint: `POST /api/v1/admin/files/backfill?scope=WALK_SPOTS&batchSize=100`
2. Monitor progress: `SELECT COUNT(*) FROM walk_spots WHERE image_url IS NOT NULL AND image_key_viewer IS NULL AND type = 'PHOTO';`
3. Expected duration: ~13 hours at 100K walk photos, ~125 photos/min single-threaded
4. Retry failed rows: rerun backfill endpoint (idempotent)

## Phase 3: Deploy Gate G1 (HARD — BLOCKS flag flip)
Run this SQL. **Flag flip BLOCKED until this returns 0:**

```sql
SELECT COUNT(*) FROM walk_spots
WHERE image_url IS NOT NULL
  AND image_key_viewer IS NULL
  AND type = 'PHOTO'
  AND created_at < '{v2_deploy_timestamp}';  -- replace with actual timestamp
```

If non-zero after 24h of backfill retries: investigate (check S3 access, HEIC decode failures, corrupt rows). Do NOT flip flag until resolved.

## Phase 4: Flag flip
1. Admin dashboard → SystemSettings → `WALK_PHOTO_VARIANTS_ENABLED = "true"`
2. Verify all pods pick up change within 60s (check `walk_photo_presigned_feature_flag_state` metric)
3. Monitor `walk_photo_variant_missing_total` alert (threshold 0.1% of served)

## Rollback
- Set `WALK_PHOTO_VARIANTS_ENABLED = "false"` — immediate; feed emits original URLs only
- If variant keys corrupted: no rollback of backfill needed; flag off means they're ignored
- If V52 migration fails: run V52R__Walk_Photo_Variants_Rollback.sql (drop columns + index)

## Forbidden state matrix
- `(WALK_PHOTO_PRESIGNED_URL_ENABLED=false, WALK_PHOTO_VARIANTS_ENABLED=true)`: INVALID — will emit raw variant keys. Runbook rejects this combo; admin UI should warn.

## Appendix — Flyway Checksum Recovery

If V52 has been previously applied to a target DB (e.g., dev environment during development) with an older checksum, Flyway will refuse to re-apply on boot with a `ValidationError: Migration checksum mismatch` error.

**Pre-deploy step (run on each target DB once before deploying fixed code):**

```sql
DELETE FROM flyway_schema_history WHERE version = '52';
```

Then on app boot Flyway re-applies V52. The migration is idempotent (`IF NOT EXISTS` / `DROP INDEX IF EXISTS`) so replay is safe. Skip this step on DBs that have never run V52.
