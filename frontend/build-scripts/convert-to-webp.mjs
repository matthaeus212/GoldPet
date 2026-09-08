#!/usr/bin/env node
/**
 * Converts 6 large static PNG/JPG assets to WebP (q=82).
 * Originals are kept for <picture> fallback support.
 */
import sharp from 'sharp';
import { stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');

const targets = [
  'public/assets/images/main/main_slide_img01.png',
  'public/assets/images/main/main_slide_img02.png',
  'public/assets/images/main/main_slide_img03.png',
  'public/assets/images/community/list_img.png',
  'public/assets/images/community/upload_img02.jpg',
  'src/assets/images/home/home_bg_top.png',
];

let totalBefore = 0;
let totalAfter = 0;

for (const rel of targets) {
  const src = path.join(root, rel);
  const dst = src.replace(/\.(png|jpg)$/i, '.webp');

  const before = (await stat(src)).size;
  await sharp(src).webp({ quality: 82 }).toFile(dst);
  const after = (await stat(dst)).size;

  const pct = (((before - after) / before) * 100).toFixed(1);
  const keep = parseFloat(pct) >= 50;
  console.log(
    `${keep ? '✓' : '✗ (<50%)'} ${rel.split('/').pop()} ${(before / 1024).toFixed(0)}KB → ${(after / 1024).toFixed(0)}KB (${pct}% saved)`
  );
  totalBefore += before;
  totalAfter += after;
}

const totalPct = (((totalBefore - totalAfter) / totalBefore) * 100).toFixed(1);
console.log(`\nTotal: ${(totalBefore / 1024).toFixed(0)}KB → ${(totalAfter / 1024).toFixed(0)}KB (${totalPct}% saved)`);
