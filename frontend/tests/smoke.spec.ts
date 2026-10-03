import { test, expect } from '@playwright/test';

test.describe('InvoiceIQ Frontend Mocks', () => {
  test.beforeEach(async ({ page }) => {
    // Mock login
    await page.route('**/api/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'mock-access-token',
          refreshToken: 'mock-refresh-token'
        })
      });
    });

    // Mock invoices list
    await page.route('**/api/invoices*', async (route) => {
      if (route.request().method() === 'GET' && !route.request().url().includes('/api/invoices/')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: [
              { id: '123', invoiceNumber: 'INV-001', vendorName: 'Test Vendor', status: 'DRAFT', totalAmount: 100, currency: 'USD', invoiceDate: '2026-01-01', dueDate: '2026-01-31' }
            ],
            totalElements: 1,
            totalPages: 1
          })
        });
      } else {
        await route.fallback();
      }
    });

    // Mock invoice details
    await page.route('**/api/invoices/123', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '123',
          invoiceNumber: 'INV-001',
          status: 'DRAFT',
          totalAmount: 100,
          taxAmount: 10,
          currency: 'USD',
          version: 1,
          invoiceDate: '2026-01-01',
          dueDate: '2026-01-31',
          vendor: { name: 'Test Vendor', taxId: 'TAX123', address: '123 Test St' },
          lineItems: []
        })
      });
    });

    // Mock workflow details
    await page.route('**/api/invoices/123/workflow', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'wf-1',
          status: 'PENDING_APPROVAL',
          currentStep: 1,
          version: 1,
          approvalSteps: [
            { id: 'step-1', stepNumber: 1, approverRole: 'APPROVER', status: 'PENDING' }
          ]
        })
      });
    });

    // Mock upload
    await page.route('**/api/invoices/123/documents', async (route) => {
      // 3. Document upload FormData request
      const request = route.request();
      expect(request.headers()['content-type']).toContain('multipart/form-data');
      await route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          documentId: 'doc-1',
          status: 'PENDING'
        })
      });
    });

    // Mock 409 conflict on approve
    let approveAttempts = 0;
    await page.route('**/api/invoices/123/approval/approve', async (route) => {
      if (approveAttempts === 0) {
        approveAttempts++;
        // 5. 409 approval conflict
        await route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Conflict' })
        });
      } else {
        await route.fulfill({ status: 200 });
      }
    });

    // Mock 401 refresh behavior
    let listAttempts = 0;
    await page.route('**/api/invoices?page=0&size=10', async (route) => {
      if (listAttempts === 0) {
        listAttempts++;
        // 4. 401 refresh behavior trigger
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Unauthorized' })
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: [],
            totalElements: 0,
            totalPages: 0
          })
        });
      }
    });

    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'new-mock-access-token',
          refreshToken: 'new-mock-refresh-token'
        })
      });
    });
  });

  test('Protected route redirects to login', async ({ page }) => {
    // 6. Protected route behavior
    await page.goto('/');
    await expect(page).toHaveURL(/.*\/login/);
  });

  test('Login, view invoice, upload document, test conflict', async ({ page }) => {
    // 2. Login/auth behavior
    await page.goto('/login');
    await page.fill('input[name="email"]', 'test@test.com');
    await page.fill('input[name="password"]', 'password');
    await page.fill('input[name="tenantId"]', '11111111-1111-1111-1111-111111111111');
    await page.click('button[type="submit"]');

    // Should redirect to dashboard
    await expect(page).toHaveURL('http://localhost:3000/');

    // Navigate to invoice page
    await page.goto('/invoices/123');
    await expect(page.getByText('Invoice #INV-001')).toBeVisible();

    // 1. API client endpoint construction & 3. Document upload FormData request
    // Set up file chooser for upload
    const fileChooserPromise = page.waitForEvent('filechooser');
    await page.getByText('Select File').click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'test.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('test')
    });
    await page.getByRole('button', { name: 'Upload Document' }).click();

    // Verify upload success (mock returns 202)
    await expect(page.getByText('Extracting Data...')).toBeVisible();

    // 5. 409 approval conflict
    // Try to approve, which will 409 the first time
    await page.getByRole('button', { name: 'Approve' }).click();
    await expect(page.getByText('Conflict: The workflow state has changed')).toBeVisible();
  });
});
