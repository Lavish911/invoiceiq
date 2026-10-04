'use client';

import { useRouter } from 'next/navigation';
import { clearTokens, getAccessToken } from '@/lib/auth-tokens';
import {
  approverIdentity,
  fetchDemoConfig,
  isDemoTenant,
  submitterIdentity,
  tenantIdFromToken,
  type DemoConfig,
} from '@/lib/demo';
import { useAuth } from '@/hooks/use-auth';
import { useEffect, useState } from 'react';

/** Shown only inside the demo workspace: ephemerality notice + role switcher. */
export function DemoBanner() {
  const { loginAsync } = useAuth();
  const router = useRouter();
  const [switching, setSwitching] = useState(false);
  const [config, setConfig] = useState<DemoConfig | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchDemoConfig()
      .then((cfg) => {
        if (!cancelled) setConfig(cfg);
      })
      .catch(() => {
        // Demo workspace unavailable: banner stays hidden.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!config || !isDemoTenant(tenantIdFromToken(getAccessToken()), config.tenantId)) {
    return null;
  }

  const switchRole = async (asApprover: boolean) => {
    setSwitching(true);
    try {
      clearTokens();
      const identity = asApprover ? approverIdentity(config) : submitterIdentity(config);
      await loginAsync({ ...identity });
      router.push('/');
    } finally {
      setSwitching(false);
    }
  };

  return (
    <div className="mb-6 rounded-lg border border-indigo-200 bg-indigo-50 p-4" role="status">
      <p className="text-sm font-medium text-indigo-900">
        Demo workspace — uploads and changes here are visible to all demo visitors
        and are reset periodically.
      </p>
      <div className="mt-3 flex gap-2">
        <button
          type="button"
          onClick={() => switchRole(false)}
          disabled={switching}
          className="rounded-md bg-white px-3 py-1.5 text-sm font-semibold text-indigo-700 shadow-sm ring-1 ring-inset ring-indigo-200 hover:bg-indigo-100 disabled:opacity-50"
        >
          View as submitter
        </button>
        <button
          type="button"
          onClick={() => switchRole(true)}
          disabled={switching}
          className="rounded-md bg-white px-3 py-1.5 text-sm font-semibold text-indigo-700 shadow-sm ring-1 ring-inset ring-indigo-200 hover:bg-indigo-100 disabled:opacity-50"
        >
          View as approver
        </button>
      </div>
    </div>
  );
}
