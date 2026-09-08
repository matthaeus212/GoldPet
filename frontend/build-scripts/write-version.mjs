// Writes dist/version.json with current git SHA + build timestamp.
// Used by Flutter WebView version checker to detect new deploys and clear cache.
import { execSync } from 'node:child_process';
import { writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const distDir = join(process.cwd(), 'dist');
if (!existsSync(distDir)) mkdirSync(distDir, { recursive: true });

let version = process.env.VITE_VERSION ?? '';
if (!version) {
  try {
    version = execSync('git rev-parse --short HEAD', { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim();
  } catch {
    version = `local-${Date.now()}`;
  }
}

const payload = { version, builtAt: new Date().toISOString() };
writeFileSync(join(distDir, 'version.json'), JSON.stringify(payload));
console.log(`[version] dist/version.json → ${JSON.stringify(payload)}`);
