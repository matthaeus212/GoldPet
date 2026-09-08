#!/usr/bin/env tsx
/**
 * upload-badge-icons.ts
 *
 * Uploads the 10 frontend badge SVGs to the admin file-service and patches
 * each badge's imageUrl via the admin API.
 *
 * Usage:
 *   ADMIN_API_BASE=https://api.mannamsquare.com ADMIN_JWT=<token> \
 *     npx tsx scripts/upload-badge-icons.ts            # dry-run
 *
 *   ADMIN_API_BASE=https://api.mannamsquare.com ADMIN_JWT=<token> \
 *     npx tsx scripts/upload-badge-icons.ts --apply    # actually upload + patch
 */

import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const BADGE_SVG_MAP: Record<number, string> = {
  1: 'badge-first-walk.svg',
  2: 'badge-neighborhood.svg',
  3: 'badge-marathoner.svg',
  4: 'badge-popular.svg',
  8: 'badge-first-family.svg',
  9: 'badge-walk-mania.svg',
  10: 'badge-first-post.svg',
  11: 'badge-community-star.svg',
  12: 'badge-empathy.svg',
  13: 'badge-hotplace.svg',
};

const REPO_ROOT = path.resolve(__dirname, '..', '..');
const BADGES_DIR = path.join(REPO_ROOT, 'frontend', 'src', 'assets', 'badges');

function printHelp(): void {
  console.log(`
upload-badge-icons.ts — Upload badge SVGs and patch imageUrl via admin API

USAGE
  npx tsx scripts/upload-badge-icons.ts [--apply] [--help]

FLAGS
  --apply    Actually upload files and PATCH badge imageUrls (default: dry-run)
  --help, -h Show this help message

ENVIRONMENT VARIABLES (required)
  ADMIN_API_BASE  Base URL of the admin API, no trailing slash
                  e.g. https://api.mannamsquare.com or http://localhost:8081
  ADMIN_JWT       Bearer token for admin API authentication

EXAMPLES
  # Dry-run (shows table, makes NO network calls)
  ADMIN_API_BASE=https://api.mannamsquare.com ADMIN_JWT=eyJ... \\
    npx tsx scripts/upload-badge-icons.ts

  # Actually upload and patch
  ADMIN_API_BASE=https://api.mannamsquare.com ADMIN_JWT=eyJ... \\
    npx tsx scripts/upload-badge-icons.ts --apply
`);
}

interface UploadResponse {
  id: number;
  url: string;
  thumbnailUrl?: string | null;
  mediumUrl?: string | null;
  originalFileName: string;
}

interface BadgeAdminResponse {
  id: number;
  imageUrl: string;
}

interface BadgeEntry {
  id: number;
  filename: string;
  svgPath: string;
  exists: boolean;
}

function getConfig(): { apiBase: string; jwt: string } {
  const apiBase = process.env.ADMIN_API_BASE;
  const jwt = process.env.ADMIN_JWT;

  const missing: string[] = [];
  if (!apiBase) missing.push('ADMIN_API_BASE');
  if (!jwt) missing.push('ADMIN_JWT');

  if (missing.length > 0) {
    console.error(`\nError: Missing required environment variable(s): ${missing.join(', ')}`);
    console.error('Set them before running the script:');
    missing.forEach((v) => console.error(`  export ${v}=<value>`));
    console.error('\nRun with --help for full usage.\n');
    process.exit(1);
  }

  return { apiBase: apiBase!.replace(/\/$/, ''), jwt: jwt! };
}

async function buildEntries(): Promise<BadgeEntry[]> {
  const sortedIds = Object.keys(BADGE_SVG_MAP).map(Number).sort((a, b) => a - b);
  return Promise.all(
    sortedIds.map(async (id) => {
      const filename = BADGE_SVG_MAP[id];
      const svgPath = path.join(BADGES_DIR, filename);
      const exists = await fs.access(svgPath).then(() => true, () => false);
      return { id, filename, svgPath, exists };
    }),
  );
}

function printTable(entries: BadgeEntry[], applyMode: boolean): void {
  const mode = applyMode ? 'APPLY' : 'DRY-RUN';
  const col1 = 4;  // id
  const col2 = 30; // filename
  const col3 = 6;  // exists
  const col4 = 8;  // mode

  const pad = (s: string, n: number) => s.padEnd(n);
  const sep = `${'-'.repeat(col1)}-|-${'-'.repeat(col2)}-|-${'-'.repeat(col3)}-|-${'-'.repeat(col4)}`;

  console.log('');
  console.log(
    `${pad('id', col1)} | ${pad('filename', col2)} | ${pad('exists', col3)} | ${pad('mode', col4)}`,
  );
  console.log(sep);

  for (const e of entries) {
    const existStr = e.exists ? '✓' : '✗';
    console.log(
      `${pad(String(e.id), col1)} | ${pad(e.filename, col2)} | ${pad(existStr, col3)} | ${mode}`,
    );
  }
  console.log('');
}

async function uploadFile(
  apiBase: string,
  jwt: string,
  entry: BadgeEntry,
): Promise<UploadResponse> {
  const fileBuffer = await fs.readFile(entry.svgPath);
  const blob = new Blob([fileBuffer], { type: 'image/svg+xml' });

  const formData = new FormData();
  formData.append('file', blob, entry.filename);
  formData.append('category', 'badge');

  const res = await fetch(`${apiBase}/api/v1/admin/files/upload`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${jwt}`,
    },
    body: formData,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '(no body)');
    throw new Error(`Upload failed [${res.status}]: ${text}`);
  }

  return res.json() as Promise<UploadResponse>;
}

async function patchBadge(
  apiBase: string,
  jwt: string,
  badgeId: number,
  imageUrl: string,
): Promise<BadgeAdminResponse> {
  const res = await fetch(`${apiBase}/api/v1/admin/gamification/badges/${badgeId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwt}`,
    },
    body: JSON.stringify({ imageUrl }),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '(no body)');
    throw new Error(`PATCH badge ${badgeId} failed [${res.status}]: ${text}`);
  }

  return res.json() as Promise<BadgeAdminResponse>;
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);

  if (args.includes('--help') || args.includes('-h')) {
    printHelp();
    process.exit(0);
  }

  const applyMode = args.includes('--apply');
  const { apiBase, jwt } = getConfig();
  const entries = await buildEntries();
  const missing = entries.filter((e) => !e.exists);

  printTable(entries, applyMode);

  if (missing.length > 0) {
    console.error('Pre-flight failed: the following SVG files are missing:');
    missing.forEach((e) => console.error(`  ${e.svgPath}`));
    process.exit(1);
  }

  if (!applyMode) {
    console.log('Dry-run complete. All 10 SVG files found. No network calls made.');
    console.log('Re-run with --apply to upload and patch.\n');
    process.exit(0);
  }

  console.log(`Uploading to: ${apiBase}\n`);

  let uploaded = 0;
  let patched = 0;
  let failed = 0;

  for (const entry of entries) {
    process.stdout.write(`[${entry.id}] ${entry.filename} — uploading... `);
    try {
      const uploadResult = await uploadFile(apiBase, jwt, entry);
      console.log(`OK → ${uploadResult.url}`);
      uploaded++;

      process.stdout.write(`[${entry.id}] patching badge imageUrl... `);
      const patchResult = await patchBadge(apiBase, jwt, entry.id, uploadResult.url);
      console.log(`OK → imageUrl=${patchResult.imageUrl}`);
      patched++;
    } catch (err) {
      console.error(`FAILED: ${(err as Error).message}`);
      failed++;
    }
  }

  console.log('');
  console.log(`Summary: ${uploaded} uploaded, ${patched} patched, ${failed} failed`);

  if (failed > 0) {
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('Unexpected error:', err);
  process.exit(1);
});
