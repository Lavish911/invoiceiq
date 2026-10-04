import { NextResponse } from 'next/server';

/**
 * Public demo workspace identity, resolved at REQUEST time from server-side
 * environment so Railway service variables apply without a rebuild
 * (NEXT_PUBLIC_* values are inlined at build time and cannot see them).
 *
 * These credentials are intentionally public and disposable: they only ever
 * reach the isolated demo tenant (enforced server-side). Never put real
 * user credentials here or in the corresponding environment variables.
 */
export async function GET() {
  const tenantId =
    process.env.DEMO_TENANT_ID ??
    process.env.NEXT_PUBLIC_DEMO_TENANT_ID ??
    '123e4567-e89b-12d3-a456-426614174999';
  const password = process.env.DEMO_PASSWORD ?? process.env.NEXT_PUBLIC_DEMO_PASSWORD ?? 'demo-password';
  const submitterEmail =
    process.env.DEMO_SUBMITTER_EMAIL ??
    process.env.NEXT_PUBLIC_DEMO_SUBMITTER_EMAIL ??
    'demo@invoiceiq.demo';
  const approverEmail =
    process.env.DEMO_APPROVER_EMAIL ??
    process.env.NEXT_PUBLIC_DEMO_APPROVER_EMAIL ??
    'demo.approver@invoiceiq.demo';
  return NextResponse.json({ tenantId, submitterEmail, approverEmail, password });
}
