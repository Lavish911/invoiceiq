import { describe, expect, it, vi } from 'vitest';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

import { loginSchema } from '../use-auth';

const VALID_TENANT = '11111111-1111-1111-1111-111111111111';

describe('loginSchema tenant validation (M8.2)', () => {
  it('accepts the structural UUID tenant fixture', () => {
    const parsed = loginSchema.safeParse({
      email: 'admin@e2e.test',
      password: 'password',
      tenantId: VALID_TENANT,
    });
    expect(parsed.success).toBe(true);
  });

  it.each(['not-a-uuid', '', '11111111-xxxx', 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'])(
    'rejects malformed tenant id %j',
    (tenantId) => {
      const parsed = loginSchema.safeParse({
        email: 'admin@e2e.test',
        password: 'password',
        tenantId,
      });
      expect(parsed.success).toBe(false);
    },
  );

  it('rejects missing tenant id and invalid email', () => {
    expect(
      loginSchema.safeParse({ email: 'admin@e2e.test', password: 'x' }).success,
    ).toBe(false);
    expect(
      loginSchema.safeParse({ email: 'not-an-email', password: 'x', tenantId: VALID_TENANT })
        .success,
    ).toBe(false);
  });
});
