/**
 * Phase 4.4 — OpenAPI contract test (runtime drift backstop).
 *
 * Hits a live API base (default: api.mannamsquare.com / dev) and asserts that
 * the server's `/v3/api-docs` matches the schema names and operationIds the
 * frontend was built against. This catches the case where the server is ahead
 * of (or behind) the committed `api/openapi.json` for any reason — e.g. a
 * forgotten regeneration step, a hotfix shipped without spec update, or an
 * environment running a stale build.
 *
 * Set `OPENAPI_CONTRACT_BASE=https://api.mannamsquare.com` (or the relevant
 * environment URL) before running. Without it the suite skips, so that local
 * `npm run test:e2e` against a non-API target stays green.
 *
 * Jenkins stage:
 *   OPENAPI_CONTRACT_BASE=https://api.mannamsquare.com \
 *   npx playwright test api-contract.spec.ts
 */

import { test, expect, request } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.OPENAPI_CONTRACT_BASE;
const __dirname = path.dirname(fileURLToPath(import.meta.url));

test.describe('OpenAPI contract', () => {
  test.skip(!BASE, 'OPENAPI_CONTRACT_BASE not set — skipping live contract checks');

  test('live spec contains every schema name and operationId we ship with', async () => {
    // __dirname = frontend/tests/e2e/ → ../../../api/openapi.json (3 단계 위, 2026-05-26 location 이동 후 보정)
    const specPath = path.join(__dirname, '..', '..', '..', 'api', 'openapi.json');
    const localRaw = fs.readFileSync(specPath, 'utf-8');
    const local = JSON.parse(localRaw) as {
      components?: { schemas?: Record<string, unknown> };
      paths?: Record<string, Record<string, { operationId?: string }>>;
    };

    const ctx = await request.newContext({ baseURL: BASE! });
    const res = await ctx.get('/v3/api-docs');
    expect(res.ok(), `live /v3/api-docs returned ${res.status()}`).toBe(true);
    const live = (await res.json()) as typeof local;

    const localSchemaNames = new Set(Object.keys(local.components?.schemas ?? {}));
    const liveSchemaNames = new Set(Object.keys(live.components?.schemas ?? {}));
    const missingSchemas = [...localSchemaNames].filter((n) => !liveSchemaNames.has(n));
    expect(missingSchemas, `live spec is missing schemas the FE depends on`).toEqual([]);

    const localOps = collectOperationIds(local.paths ?? {});
    const liveOps = collectOperationIds(live.paths ?? {});
    const missingOps = [...localOps].filter((id) => !liveOps.has(id));
    expect(missingOps, `live spec is missing operationIds the FE depends on`).toEqual([]);
  });
});

function collectOperationIds(
  paths: Record<string, Record<string, { operationId?: string }>>,
): Set<string> {
  const ids = new Set<string>();
  for (const methods of Object.values(paths)) {
    for (const op of Object.values(methods)) {
      if (op?.operationId) ids.add(op.operationId);
    }
  }
  return ids;
}
