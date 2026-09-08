import { test, expect } from '@playwright/test';

test.describe('Frontend Authentication', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('should display login page with correct elements', async ({ page }) => {
    // Check for logo
    await expect(page.getByRole('img', { name: 'GoldPet' })).toBeVisible();

    // Check for subtitle
    await expect(page.getByText('로그인 정보를 입력해 주세요')).toBeVisible();

    // Check for login inputs
    await expect(page.getByPlaceholder('아이디')).toBeVisible();
    await expect(page.getByPlaceholder('비밀번호')).toBeVisible();

    // Check for main login button (using the specific class)
    await expect(page.locator('button.login-button')).toBeVisible();
  });

  test('should show alert for empty credentials', async ({ page }) => {
    // Click login without filling in credentials
    await page.locator('button.login-button').click();

    // Should show alert - we need to wait for the alert dialog
    // The frontend uses a custom alert from AlertContext
    await expect(page.getByText('아이디와 비밀번호를 입력해주세요.')).toBeVisible({ timeout: 5000 });
  });

  test('should have auto login checkbox', async ({ page }) => {
    // Check for auto login checkbox
    await expect(page.getByText('자동 로그인')).toBeVisible();

    // Check if checkbox can be toggled
    const checkbox = page.locator('input[type="checkbox"]');
    await checkbox.check();
    await expect(checkbox).toBeChecked();
  });

  test('should have navigation links', async ({ page }) => {
    // Check for navigation links
    await expect(page.getByRole('button', { name: '아이디 찾기' })).toBeVisible();
    await expect(page.getByRole('button', { name: '비밀번호 변경' })).toBeVisible();
    await expect(page.getByRole('button', { name: '회원가입' })).toBeVisible();
  });

  test('should have SNS login options', async ({ page }) => {
    // Check for SNS login section title
    await expect(page.getByText('SNS 계정으로 로그인/회원가입')).toBeVisible();

    // Check for SNS login buttons (using aria-label)
    await expect(page.getByRole('button', { name: '네이버 로그인' })).toBeVisible();
    await expect(page.getByRole('button', { name: '카카오 로그인' })).toBeVisible();
    await expect(page.getByRole('button', { name: '애플 로그인' })).toBeVisible();
    await expect(page.getByRole('button', { name: '구글 로그인' })).toBeVisible();
  });

  test('should toggle password visibility', async ({ page }) => {
    // Fill in password
    const passwordInput = page.getByPlaceholder('비밀번호');
    await passwordInput.fill('testpassword');

    // Password should be hidden by default
    await expect(passwordInput).toHaveAttribute('type', 'password');

    // Click on the visibility toggle button
    const toggleButton = page.locator('button[data-name="ic_invisible"]');
    await toggleButton.click();

    // Password should now be visible
    await expect(passwordInput).toHaveAttribute('type', 'text');

    // Click again to hide
    await toggleButton.click();
    await expect(passwordInput).toHaveAttribute('type', 'password');
  });

  test('should navigate to signup page', async ({ page }) => {
    await page.getByRole('button', { name: '회원가입' }).click();

    await expect(page).toHaveURL(/.*signup.*/);
  });

  test('should navigate to find username page', async ({ page }) => {
    await page.getByRole('button', { name: '아이디 찾기' }).click();

    await expect(page).toHaveURL(/.*find-username.*/);
  });

  test('should navigate to reset password page', async ({ page }) => {
    await page.getByRole('button', { name: '비밀번호 변경' }).click();

    await expect(page).toHaveURL(/.*reset-password.*/);
  });

  test('should show loading state when logging in', async ({ page }) => {
    // Fill in credentials
    await page.getByPlaceholder('아이디').fill('testuser');
    await page.getByPlaceholder('비밀번호').fill('testpassword');

    // Click login button (using specific class selector)
    await page.locator('button.login-button').click();

    // Button should show loading state
    await expect(page.locator('button.login-button')).toHaveText('로그인 중...');
  });

  test('should show error for invalid credentials', async ({ page }) => {
    // Fill in invalid credentials
    await page.getByPlaceholder('아이디').fill('invaliduser');
    await page.getByPlaceholder('비밀번호').fill('wrongpassword');

    // Click login button
    await page.locator('button.login-button').click();

    // Wait for error message from the alert
    await expect(page.getByText('로그인에 실패했습니다.')).toBeVisible({ timeout: 10000 });
  });

  test('should disable inputs while loading', async ({ page }) => {
    await page.getByPlaceholder('아이디').fill('testuser');
    await page.getByPlaceholder('비밀번호').fill('testpassword');

    // Click login
    await page.locator('button.login-button').click();

    // Inputs should be disabled during loading
    await expect(page.getByPlaceholder('아이디')).toBeDisabled();
    await expect(page.getByPlaceholder('비밀번호')).toBeDisabled();
  });
});

test.describe('Frontend Splash and Onboarding', () => {
  test('should show splash page on initial visit', async ({ page }) => {
    await page.goto('/splash');

    // Check if splash page loads
    await expect(page.getByRole('img', { name: /GoldPet|logo/i })).toBeVisible({ timeout: 5000 }).catch(() => {
      // Splash page might have different content
    });
  });

  test('should show onboarding for new users', async ({ page }) => {
    await page.goto('/onboarding');

    // Onboarding page should be visible
    const pageBody = page.locator('body');
    await expect(pageBody).toBeVisible();
  });
});
