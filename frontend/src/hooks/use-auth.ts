import { useMutation } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { setTokens, clearTokens } from '@/lib/auth-tokens';
import { useRouter } from 'next/navigation';
import { z } from 'zod';

export const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
  tenantId: z.string().regex(/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/i, 'Invalid Tenant ID format (must be UUID)'),
});

export type LoginFormData = z.infer<typeof loginSchema>;

export const useAuth = () => {
  const router = useRouter();

  const loginMutation = useMutation({
    mutationFn: async (data: LoginFormData) => {
      // apiClient response interceptor already extracts `.data`
      return apiClient.post('/auth/login', data) as Promise<{ accessToken: string; refreshToken: string }>;
    },
    onSuccess: (data) => {
      setTokens(data.accessToken, data.refreshToken);
      router.push('/'); // Redirect to protected dashboard/root
    },
  });

  const logout = () => {
    clearTokens();
    router.push('/login');
  };

  return {
    login: loginMutation.mutate,
    loginAsync: loginMutation.mutateAsync,
    isLoggingIn: loginMutation.isPending,
    loginError: loginMutation.error,
    logout,
  };
};
