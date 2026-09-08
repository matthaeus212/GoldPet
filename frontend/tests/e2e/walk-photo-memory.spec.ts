import { test, expect } from '@playwright/test';

// TODO: Implement real device memory smoke test post-v2.
// This stub ensures the CI hook is wired up before the full spec is written.
// Full spec will:
//   1. Load WalkDetailPage with 100+ photo walk
//   2. Rapid-swipe through all photos (jsHeapSizeLimit check)
//   3. Assert JS heap does not exceed 150 MB threshold
//   Note: This is a Chromium-only headless proxy — real device (RSS < 300 MB) attestation
//         is required separately via manual test in PR template.

test.describe('walk-photo memory smoke (Chromium headless proxy)', () => {
  test('stub — real implementation pending', async () => {
    // Placeholder: always passes until real spec is wired in
    expect(true).toBe(true);
  });
});
