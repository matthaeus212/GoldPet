import { execFileSync } from 'child_process';

/**
 * Playwright globalSetup — idempotent admin seed
 *
 * Ensures the two admin accounts used by E2E specs exist with correct
 * password and is_active=true before any spec runs. ON CONFLICT DO UPDATE
 * makes it safe to run repeatedly.
 *
 * Precomputed values (key: local-dev-encryption-key-32ch!! zero-padded to 32 bytes):
 *   email_hash = HMAC-SHA256(email)
 *   email/name = AES-256-GCM(plaintext, IV=01..0c)
 */

type AdminSeed = {
  emailHash: string;
  encryptedEmail: string;
  encryptedName: string;
  passwordHash: string;
};

// e2eadmin@goldpet.com / Admin!e2e1 — used by notice-popup, place-edit-concurrency
const E2E_ADMIN: AdminSeed = {
  emailHash: 'nHBoyv80fA+0WosAMR0CYpTh9cYU7fV1S+mdg7XWv00=',
  encryptedEmail: 'AQIDBAUGBwgJCgsMrwrO68HurOOR1ik3Phzr0HEqqZSl+csisDKOBXujoD/4tyyv',
  encryptedName: 'AQIDBAUGBwgJCgsMjwruquTnqOS/V2ztGeMpT2haKlwXrwyUTg==',
  passwordHash: '$2b$10$asDflNfvUi3e.W8MiOHpxeou4kpAkLWdWTOXXmdhj/ZoQHlJMk64C',
};

// admin@goldpet.com / admin123 — used by auth.spec.ts (default admin)
const DEFAULT_ADMIN: AdminSeed = {
  emailHash: 'vJNIF6W4WbNG5HS2JrJgByVUre3Pf2vM789sHZyCH5I=',
  encryptedEmail: 'AQIDBAUGBwgJCgsMq1zG48vDouK91TY+LkLtyzK4DB77AW0tqt+NV1McBMRO',
  encryptedName: 'AQIDBAUGBwgJCgsMi1zG48ujgei30DM3LviuN9axuD6BavO+kY7JH08=',
  passwordHash: '$2b$10$gtY/yOEzHWfA6RBdBGmvJ.x5jvwOi9xlV7qHIzIEOMpPi5fykjEhe',
};

function buildSql(seed: AdminSeed): string {
  return [
    'INSERT INTO admin_users',
    '  (email, email_hash, password_hash, name, role, is_active, must_change_password)',
    'VALUES',
    `  ('${seed.encryptedEmail}', '${seed.emailHash}', '${seed.passwordHash}',`,
    `   '${seed.encryptedName}', 'SUPER_ADMIN', true, false)`,
    'ON CONFLICT (email_hash) DO UPDATE SET',
    '  password_hash        = EXCLUDED.password_hash,',
    '  is_active            = true,',
    '  must_change_password = false,',
    "  role                 = 'SUPER_ADMIN';",
  ].join('\n');
}

function runSql(sql: string): void {
  execFileSync(
    'psql',
    ['-h', 'localhost', '-p', '5433', '-U', 'goldpet', '-d', 'goldpet', '-c', sql],
    { env: { ...process.env, PGPASSWORD: 'goldpet123' }, stdio: 'pipe' },
  );
}

export default async function globalSetup() {
  runSql(buildSql(E2E_ADMIN));
  runSql(buildSql(DEFAULT_ADMIN));
  console.log('[global-setup] e2eadmin + admin@ seed OK');
}
