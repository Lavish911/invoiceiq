import { z } from 'zod';

const envSchema = z.object({
  NEXT_PUBLIC_API_URL: z.string().default('/api'),
  NEXT_PUBLIC_APP_NAME: z.string().default('InvoiceIQ'),
  // Disposable demo workspace identity. These credentials are intentionally
  // public: the demo account can only ever reach the isolated demo tenant.
  // Never reuse real user credentials here.
  NEXT_PUBLIC_DEMO_TENANT_ID: z.string().default('123e4567-e89b-12d3-a456-426614174999'),
  NEXT_PUBLIC_DEMO_SUBMITTER_EMAIL: z.string().default('demo@invoiceiq.demo'),
  NEXT_PUBLIC_DEMO_APPROVER_EMAIL: z.string().default('demo.approver@invoiceiq.demo'),
  NEXT_PUBLIC_DEMO_PASSWORD: z.string().default('demo-password'),
});

export const env = envSchema.parse({
  NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
  NEXT_PUBLIC_APP_NAME: process.env.NEXT_PUBLIC_APP_NAME,
  NEXT_PUBLIC_DEMO_TENANT_ID: process.env.NEXT_PUBLIC_DEMO_TENANT_ID,
  NEXT_PUBLIC_DEMO_SUBMITTER_EMAIL: process.env.NEXT_PUBLIC_DEMO_SUBMITTER_EMAIL,
  NEXT_PUBLIC_DEMO_APPROVER_EMAIL: process.env.NEXT_PUBLIC_DEMO_APPROVER_EMAIL,
  NEXT_PUBLIC_DEMO_PASSWORD: process.env.NEXT_PUBLIC_DEMO_PASSWORD,
});
