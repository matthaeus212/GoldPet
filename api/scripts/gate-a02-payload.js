/**
 * gate-a02-payload.js  — A0-2 Gate: 샘플 100 viewer URL Content-Length 평균 −60%
 *
 * 사용법:
 *   # Baseline (Tier 0 배포 전):
 *   DB_URL="postgresql://user:pass@host:5432/dbname" node gate-a02-payload.js baseline
 *
 *   # After (Tier 0 배포 후):
 *   DB_URL="postgresql://user:pass@host:5432/dbname" node gate-a02-payload.js after
 *
 *   # Compare (baseline.json + after.json 이 같은 디렉토리에 있어야 함):
 *   node gate-a02-payload.js compare
 *
 * 출력: Slack 붙여넣기 가능 포맷 (plain text)
 * exit code: 0=pass, 1=fail/error
 *
 * 의존성: node >= 18 (fetch built-in), pg (npm install pg)
 */

'use strict';

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// ── helpers ──────────────────────────────────────────────────────────────────

function usage() {
  console.error('Usage: DB_URL=<postgresql://...> node gate-a02-payload.js <baseline|after|compare>');
  process.exit(1);
}

function hr() { console.log('─'.repeat(60)); }

async function fetchContentLength(url) {
  try {
    const res = await fetch(url, { method: 'HEAD', signal: AbortSignal.timeout(10_000) });
    const cl = parseInt(res.headers.get('content-length') ?? '0', 10);
    return cl > 0 ? cl : null;
  } catch {
    return null;
  }
}

async function queryViewerUrls(dbUrl, limit = 100) {
  let pg;
  try { pg = require('pg'); } catch {
    console.error('[ERROR] pg module not found. Run: npm install pg');
    process.exit(1);
  }
  const client = new pg.Client({ connectionString: dbUrl });
  await client.connect();
  const sql = `
    SELECT viewer_url
    FROM   file_attachments
    WHERE  file_type   = 'IMAGE'
      AND  mime_type  <> 'image/gif'
      AND  viewer_url IS NOT NULL
    ORDER  BY created_at DESC
    LIMIT  $1
  `;
  const { rows } = await client.query(sql, [limit]);
  await client.end();
  return rows.map(r => r.viewer_url);
}

async function measure(dbUrl) {
  console.log(`[INFO] DB 에서 최신 viewer_url ${100}개 조회 중...`);
  const urls = await queryViewerUrls(dbUrl, 100);
  if (urls.length === 0) {
    console.error('[FAIL] viewer_url 이 0건입니다. A0-1 gate 부터 확인하세요.');
    process.exit(1);
  }
  console.log(`[INFO] ${urls.length}개 URL 에 HEAD 요청 중...`);

  const results = await Promise.allSettled(
    urls.map(url => fetchContentLength(url))
  );

  const sizes = results
    .map(r => (r.status === 'fulfilled' ? r.value : null))
    .filter(v => v !== null);

  const avg = sizes.reduce((s, v) => s + v, 0) / sizes.length;
  return { sampleCount: sizes.length, avgBytes: Math.round(avg), urls: urls.slice(0, 5) };
}

// ── main ──────────────────────────────────────────────────────────────────────

const mode = process.argv[2];
if (!['baseline', 'after', 'compare'].includes(mode)) usage();

const BASELINE_FILE = path.join(__dirname, 'a02-baseline.json');
const AFTER_FILE    = path.join(__dirname, 'a02-after.json');

(async () => {
  if (mode === 'baseline' || mode === 'after') {
    const dbUrl = process.env.DB_URL;
    if (!dbUrl) { console.error('[ERROR] DB_URL env var required'); process.exit(1); }

    const data = await measure(dbUrl);
    const file = mode === 'baseline' ? BASELINE_FILE : AFTER_FILE;
    fs.writeFileSync(file, JSON.stringify({ ...data, measuredAt: new Date().toISOString() }, null, 2));

    hr();
    console.log(`[A0-2] Mode    : ${mode}`);
    console.log(`[A0-2] Samples : ${data.sampleCount}`);
    console.log(`[A0-2] Avg size: ${(data.avgBytes / 1024).toFixed(1)} KB (${data.avgBytes} bytes)`);
    console.log(`[INFO] 결과 저장: ${file}`);
    hr();

  } else {
    // compare
    if (!fs.existsSync(BASELINE_FILE) || !fs.existsSync(AFTER_FILE)) {
      console.error('[ERROR] baseline 과 after JSON 파일이 모두 필요합니다.');
      process.exit(1);
    }
    const base  = JSON.parse(fs.readFileSync(BASELINE_FILE, 'utf8'));
    const after = JSON.parse(fs.readFileSync(AFTER_FILE,    'utf8'));
    const reduction = (base.avgBytes - after.avgBytes) / base.avgBytes;
    const pass = reduction >= 0.60;

    hr();
    console.log('=== A0-2: Viewer Payload 크기 감소율 Gate ===');
    console.log(`Baseline  : ${(base.avgBytes  / 1024).toFixed(1)} KB  (${base.measuredAt})`);
    console.log(`After     : ${(after.avgBytes / 1024).toFixed(1)} KB  (${after.measuredAt})`);
    console.log(`Reduction : ${(reduction * 100).toFixed(1)}%  (target ≥ 60%)`);
    console.log(`Result    : ${pass ? 'PASS ✓' : 'FAIL ✗'}`);
    hr();
    if (!pass) {
      console.error('[FAIL] viewer_url LIKE \'%_viewer.jpg\' 샘플로 longSide 교정 확인 필요');
      process.exit(1);
    }
  }
})().catch(err => { console.error('[ERROR]', err.message); process.exit(1); });
