import { test, expect } from '@playwright/test';

// Mirrors the seeded demo login documented in AGENTS.md / DataInitializer.
const USERNAME = process.env.MOCKINGBIRD_DEV_USERNAME ?? 'jordan.ellis';
const PASSWORD = process.env.MOCKINGBIRD_DEV_PASSWORD ?? 'mockingbird';

test('signing in shows the seeded accounts and their transactions', async ({ page }) => {
  await page.goto('/');

  // Unauthenticated -> redirected to the Vaadin login overlay.
  await expect(page).toHaveURL(/\/login/);
  await page.locator('input[name="username"]').fill(USERNAME);
  await page.locator('input[name="password"]').fill(PASSWORD);
  await page.getByRole('button', { name: 'Log in' }).click();

  // Dashboard: greeting, total assets hero, and all three seeded accounts.
  await expect(page.getByText('Welcome back, Jordan Ellis')).toBeVisible();
  await expect(page.getByText('Total assets')).toBeVisible();
  await expect(page.getByText('Your accounts')).toBeVisible();
  await expect(page.getByText('Everyday Checking')).toBeVisible();
  await expect(page.getByText('High-Yield Savings')).toBeVisible();
  await expect(page.getByText('Vacation Savings')).toBeVisible();

  // Drill into one account and confirm its seeded transactions render.
  await page.getByText('Everyday Checking').click();
  await expect(page).toHaveURL(/\/accounts\/.+/);
  await expect(page.getByText("Trader Joe's")).toBeVisible();
  // Seeded twice (two income deposits) - assert at least one renders rather
  // than pin an exact count that's an implementation detail of the seed data.
  await expect(page.getByText('Direct Deposit - Bespin Engineering').first()).toBeVisible();

  // Sign out returns to the login overlay - proves the session, not just the
  // page load, is real.
  await page.goBack();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login/);
});

test('a wrong password is rejected with the login overlay error state', async ({ page }) => {
  await page.goto('/login');
  await page.locator('input[name="username"]').fill(USERNAME);
  await page.locator('input[name="password"]').fill('definitely-wrong');
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page).toHaveURL(/\/login\?error/);
  await expect(page.getByText(USERNAME)).not.toBeVisible();
});

test('eye toggles reveal and re-mask full account numbers', async ({ page }) => {
  await page.goto('/login');
  await page.locator('input[name="username"]').fill(USERNAME);
  await page.locator('input[name="password"]').fill(PASSWORD);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByText('Your accounts')).toBeVisible();

  // Seeded numbers from DataInitializer - never in the page until explicitly revealed.
  const checking = '1000000004821';
  const savings = '2000000007743';
  await expect(page.getByText(checking)).toHaveCount(0);

  // Per-account eye on the Everyday Checking card: reveals in place without navigating.
  const checkingCard = page.locator('a', { hasText: 'Everyday Checking' });
  await checkingCard.locator('.account-number-toggle').click();
  await expect(checkingCard.getByText(checking)).toBeVisible();
  await expect(page).not.toHaveURL(/\/accounts\/.+/);
  await checkingCard.locator('.account-number-toggle').click();
  await expect(page.getByText(checking)).toHaveCount(0);

  // Global toggle at the top of the dashboard reveals and hides every number at once.
  await page.getByRole('button', { name: 'Show all account numbers' }).click();
  await expect(page.getByText(savings)).toBeVisible();
  await expect(page.getByText(checking).first()).toBeVisible();
  await page.getByRole('button', { name: 'Hide all account numbers' }).click();
  await expect(page.getByText(savings)).toHaveCount(0);
  await expect(page.getByText(checking)).toHaveCount(0);
});
