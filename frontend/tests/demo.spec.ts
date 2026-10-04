import { test, expect, type Page } from '@playwright/test';

const DEMO_TENANT = '123e4567-e89b-12d3-a456-426614174999';

const demoJwt = (tenantId: string) => {
  const b64 = (o: unknown) => Buffer.from(JSON.stringify(o)).toString('base64url');
  return `${b64({ alg: 'none' })}.${b64({ tenantId })}.sig`;
};

const json = (status: number, body: unknown) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body),
});

const demoConfig = {
  tenantId: DEMO_TENANT,
  submitterEmail: 'demo@invoiceiq.demo',
  approverEmail: 'demo.approver@invoiceiq.demo',
  password: 'demo-password',
};

function mockDemoConfig(page: Page) {
  return page.route('**/api/demo-config', async (route) => {
    await route.fulfill(json(200, demoConfig));
  });
}

function watchCleanliness(page: Page) {
  const problems: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error') problems.push(`console: ${m.text()}`);
  });
  page.on('pageerror', (e) => problems.push(`pageerror: ${String(e)}`));
  page.on('response', (r) => {
    if (r.url().includes('/api/') && r.status() >= 400) {
      problems.push(`api ${r.status()}: ${r.url()}`);
    }
  });
  return problems;
}

const seedList = {
  content: [
    {
      id: '11111111-2222-3333-4444-555555555555',
      invoiceNumber: 'DEMO-001',
      vendor: { id: 'v1', name: 'Demo Vendor' },
      status: 'DRAFT',
      totalAmount: 100,
      currency: 'USD',
    },
  ],
  totalElements: 1,
};

test.describe('Demo workspace (M9)', () => {
  test('Try Demo signs into the seeded dashboard with a clean console', async ({ page }) => {
    const problems = watchCleanliness(page);
    const logins: Array<unknown> = [];
    await mockDemoConfig(page);
    await page.route('**/api/auth/login', async (route) => {
      logins.push(route.request().postDataJSON());
      await route.fulfill(json(200, { accessToken: demoJwt(DEMO_TENANT), refreshToken: 'r' }));
    });
    await page.route('**/api/invoices*', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill(json(200, seedList));
      } else {
        await route.fallback();
      }
    });

    await page.goto('/login');
    await page.getByRole('button', { name: 'Sign in' }).waitFor({ timeout: 90000 });
    await page.getByRole('button', { name: /try demo/i }).click();
    await expect(page).toHaveURL('/');
    await expect(page.getByText('Demo workspace')).toBeVisible();
    await expect(page.getByText('DEMO-001')).toBeVisible();

    expect(logins).toEqual([
      expect.objectContaining({ tenantId: DEMO_TENANT }),
    ]);
    expect(problems).toEqual([]);
  });

  test('Role switcher logs in as the approver identity', async ({ page }) => {
    const problems = watchCleanliness(page);
    const logins: Array<unknown> = [];
    await mockDemoConfig(page);
    await page.route('**/api/auth/login', async (route) => {
      logins.push(route.request().postDataJSON());
      await route.fulfill(json(200, { accessToken: demoJwt(DEMO_TENANT), refreshToken: 'r' }));
    });
    await page.route('**/api/invoices*', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill(json(200, seedList));
      } else {
        await route.fallback();
      }
    });

    await page.goto('/login');
    await page.getByRole('button', { name: 'Sign in' }).waitFor({ timeout: 90000 });
    await page.getByRole('button', { name: /try demo/i }).click();
    await expect(page).toHaveURL('/');
    await page.getByRole('button', { name: /view as approver/i }).click();
    await expect
      .poll(() => logins.length, { timeout: 10000 })
      .toBe(2);
    expect(logins[1]).toEqual(
      expect.objectContaining({ email: expect.stringContaining('approver') }),
    );
    expect(problems).toEqual([]);
  });
});
