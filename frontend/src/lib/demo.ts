import { env } from './env';

/**
 * Disposable demo workspace identity. These credentials are intentionally
 * public and must never belong to a real user: the demo accounts can only
 * ever reach the isolated demo tenant (enforced server-side by tenant
 * isolation + RBAC), and demo uploads are quota-limited and periodically
 * purged (see backend DemoQuotaService / DemoCleanupService).
 */
export const DEMO_TENANT_ID = env.NEXT_PUBLIC_DEMO_TENANT_ID;
export interface DemoIdentity {
  email: string;
  password: string;
  tenantId: string;
}

export interface DemoConfig {
  tenantId: string;
  submitterEmail: string;
  approverEmail: string;
  password: string;
}

/** Runtime demo config from the same-origin API route (never build-inlined). */
export async function fetchDemoConfig(): Promise<DemoConfig> {
  const res = await fetch('/api/demo-config', { cache: 'no-store' });
  if (!res.ok) {
    throw new Error('Demo workspace is not configured.');
  }
  return (await res.json()) as DemoConfig;
}

export function submitterIdentity(config: DemoConfig): DemoIdentity {
  return { email: config.submitterEmail, password: config.password, tenantId: config.tenantId };
}

export function approverIdentity(config: DemoConfig): DemoIdentity {
  return { email: config.approverEmail, password: config.password, tenantId: config.tenantId };
}

/** True when the given tenant is the demo workspace (role switcher scoping). */
export function isDemoTenant(tenantId: string | null | undefined, demoTenantId: string): boolean {
  return !!tenantId && tenantId === demoTenantId;
}

/** Tenant UUID encoded inside a JWT access token, or null when absent. */
export function tenantIdFromToken(token: string | undefined): string | null {
  if (!token) return null;
  try {
    const segment = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(segment)) as { tenantId?: unknown };
    return typeof payload.tenantId === 'string' ? payload.tenantId : null;
  } catch {
    return null;
  }
}
