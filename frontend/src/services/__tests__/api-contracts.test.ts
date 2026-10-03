/**
 * M8.2 regression harness: service-level API contracts + upload wire encoding.
 * No network, no backend, no secrets — axios is intercepted at the adapter.
 */
import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { afterEach, describe, expect, it } from 'vitest';

import { apiClient } from '@/lib/api-client';
import { clearTokens, isAuthenticated, setTokens } from '@/lib/auth-tokens';
import { documentApi } from '@/services/document-service';
import { invoiceApi } from '@/services/invoice-service';
import { workflowApi } from '@/services/workflow-service';

const IID = '11111111-2222-3333-4444-555555555555';
const DID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

let mockApi: MockAdapter;
let mockPlain: MockAdapter;

function setup() {
  clearTokens();
  mockApi = new MockAdapter(apiClient);
  mockPlain = new MockAdapter(axios);
}

afterEach(() => {
  mockApi?.reset();
  mockPlain?.reset();
  clearTokens();
});

describe('auth contract', () => {
  it('posts login path/body and returns tokens', async () => {
    setup();
    const pair = { accessToken: 'a1', refreshToken: 'r1' };
    let seenBody: unknown;
    mockApi.onPost('/api/auth/login').reply((config) => {
      seenBody = JSON.parse(config.data as string);
      return [200, pair];
    });

    const res = await apiClient.post('/auth/login', {
      email: 'admin@e2e.test',
      password: 'pw',
      tenantId: '11111111-1111-1111-1111-111111111111',
    });

    expect(seenBody).toEqual({
      email: 'admin@e2e.test',
      password: 'pw',
      tenantId: '11111111-1111-1111-1111-111111111111',
    });
    expect(res).toEqual(pair);
  });

  it('rotates tokens through the 401 interceptor chain', async () => {
    setup();
    setTokens('expired-access', 'good-refresh');
    mockApi.onGet('/api/invoices').replyOnce(401);
    mockApi.onGet('/api/invoices').reply(200, { content: [], totalElements: 0 });
    // The interceptor retries through the global axios instance (bypassing
    // apiClient), so the retry leg needs its own mock on that instance.
    mockPlain.onGet('/api/invoices').reply(200, { content: [], totalElements: 0 });
    mockPlain.onPost('/api/auth/refresh').reply(200, {
      accessToken: 'fresh-access',
      refreshToken: 'fresh-refresh',
    });

    const res = await apiClient.get('/invoices');

    expect(res).toEqual({ content: [], totalElements: 0 });
    // rotation persisted the fresh access token for subsequent calls
    const followUpHeaders: Array<unknown> = [];
    mockApi.onGet('/api/invoices').reply((config) => {
      followUpHeaders.push(config.headers?.Authorization);
      return [200, { content: [] }];
    });
    await apiClient.get('/invoices');
    expect(followUpHeaders).toEqual(['Bearer fresh-access']);
  });

  it('clears tokens and surfaces 401 when no refresh token exists', async () => {
    setup();
    setTokens('expired-access', '');
    // empty refresh token is falsy -> interceptor clears and rejects
    clearTokens();
    mockApi.onGet('/api/invoices').reply(401);
    await expect(apiClient.get('/invoices')).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(isAuthenticated()).toBe(false);
  });

  it('logout clears tokens', () => {
    setup();
    setTokens('a', 'r');
    expect(isAuthenticated()).toBe(true);
    clearTokens();
    expect(isAuthenticated()).toBe(false);
  });
});

describe('upload wire encoding (M8.2 boundary regression)', () => {
  it('sends FormData without forcing application/json', async () => {
    setup();
    setTokens('tok', 'ref');
    let contentType: unknown = 'unset-marker';
    let sentData: unknown;
    mockApi.onPost(`/api/invoices/${IID}/documents`).reply((config) => {
      contentType = config.headers?.['Content-Type'];
      sentData = config.data;
      return [202, { id: DID, status: 'UPLOADED', message: 'queued' }];
    });

    const file = new File(['%PDF-1.4 fake'], 't.pdf', { type: 'application/pdf' });
    const res = await documentApi.uploadDocument(IID, file);

    // Regression guard: the pre-fix code forced application/json, which
    // JSON-stringifies FormData and drops the file on the browser path.
    expect(contentType).not.toBe('application/json');
    expect(sentData).toBeInstanceOf(FormData);
    expect(res).toEqual({ id: DID, status: 'UPLOADED', message: 'queued' });
  });

  it('reads document and extraction results', async () => {
    setup();
    mockApi.onGet(`/api/invoices/${IID}/documents/${DID}`).reply(200, { id: DID });
    mockApi.onGet(`/api/invoices/${IID}/documents/${DID}/extraction`).reply(200, {
      status: 'COMPLETED',
    });
    await expect(documentApi.getDocument(IID, DID)).resolves.toEqual({ id: DID });
    await expect(documentApi.getExtractionResult(IID, DID)).resolves.toEqual({
      status: 'COMPLETED',
    });
  });
});

describe('invoice contract', () => {
  it('lists with pagination params and returns the page', async () => {
    setup();
    let seenParams: unknown;
    const page = { content: [{ id: IID }], totalElements: 1 };
    mockApi.onGet('/api/invoices').reply((config) => {
      seenParams = config.params;
      return [200, page];
    });

    const res = await invoiceApi.getAll({ page: 0, size: 100, sort: 'createdAt,desc' });

    expect(seenParams).toEqual({ page: 0, size: 100, sort: 'createdAt,desc' });
    expect(res).toEqual(page);
  });

  it('creates (201) and submits invoices', async () => {
    setup();
    const body = {
      vendorId: 'v1',
      invoiceNumber: 'N-1',
      invoiceDate: '2026-10-03',
      dueDate: '2026-11-03',
      currency: 'USD',
      lineItems: [{ description: 'x', quantity: 1, unitPrice: 10 }],
    };
    let seenCreate: unknown;
    mockApi.onPost('/api/invoices').reply((config) => {
      seenCreate = JSON.parse(config.data as string);
      return [201, { id: IID }];
    });
    mockApi.onGet(`/api/invoices/${IID}`).reply(200, { id: IID });
    mockApi.onPost(`/api/invoices/${IID}/submit`).reply(200, { id: IID });

    await expect(invoiceApi.getById(IID)).resolves.toEqual({ id: IID });

    const created = await apiClient.post('/invoices', body);
    expect(seenCreate).toEqual(body);
    expect(created).toEqual({ id: IID });
    await expect(workflowApi.submit(IID)).resolves.toEqual({ id: IID });
  });
});

describe('workflow contract', () => {
  it('initiates, retrieves, approves and rejects with expectedVersion', async () => {
    setup();
    const seen: Record<string, unknown> = {};
    mockApi.onPost(`/api/invoices/${IID}/submit`).reply(200, { id: IID });
    mockApi.onPost(`/api/invoices/${IID}/workflow/initiate`).reply(200);
    mockApi.onGet(`/api/invoices/${IID}/workflow`).reply(200, { status: 'PENDING_APPROVAL' });
    mockApi.onPost(`/api/invoices/${IID}/approval/approve`).reply((config) => {
      seen.approve = JSON.parse(config.data as string);
      return [200];
    });
    mockApi.onPost(`/api/invoices/${IID}/approval/reject`).reply((config) => {
      seen.reject = JSON.parse(config.data as string);
      return [200];
    });

    await expect(workflowApi.submit(IID)).resolves.toEqual({ id: IID });
    await expect(workflowApi.initiate(IID)).resolves.toBeUndefined();
    await expect(workflowApi.getWorkflow(IID)).resolves.toEqual({
      status: 'PENDING_APPROVAL',
    });
    await workflowApi.approve(IID, { comment: 'ok', expectedVersion: 3 });
    await workflowApi.reject(IID, { comment: 'no', expectedVersion: 3 });
    expect(seen.approve).toEqual({ comment: 'ok', expectedVersion: 3 });
    expect(seen.reject).toEqual({ comment: 'no', expectedVersion: 3 });
  });
});

describe('error contracts', () => {
  it('surfaces 403 from approval', async () => {
    setup();
    mockApi.onPost(`/api/invoices/${IID}/approval/approve`).reply(403, {
      message: 'Access denied',
    });
    await expect(
      workflowApi.approve(IID, { comment: '', expectedVersion: 0 }),
    ).rejects.toMatchObject({ response: { status: 403 } });
  });

  it('surfaces 409 conflicts', async () => {
    setup();
    mockApi.onPost(`/api/invoices/${IID}/approval/approve`).reply(409, {
      message: 'conflict',
    });
    await expect(
      workflowApi.approve(IID, { comment: '', expectedVersion: 0 }),
    ).rejects.toMatchObject({ response: { status: 409 } });
  });
});
