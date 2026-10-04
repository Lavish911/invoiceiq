'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth, loginSchema, type LoginFormData } from '@/hooks/use-auth';
import { fetchDemoConfig, submitterIdentity } from '@/lib/demo';
import { useState } from 'react';
import { ErrorAlert } from '@/components/ui/error-alert';
import { Spinner } from '@/components/ui/spinner';
import { isAxiosError } from 'axios';

function getLoginErrorMessage(error: Error): string {
  if (isAxiosError(error)) {
    const status = error.response?.status;
    if (status === 401) return 'Invalid email, password, or tenant ID.';
    if (status === 403) return 'Your account has been locked or disabled. Contact your administrator.';
    if (status === 429) return 'Too many login attempts. Please wait and try again.';
    if (status && status >= 500) return 'Server error. Please try again later.';
    if (!error.response) return 'Network error. Please check your connection.';
    return error.response.data?.message || 'Authentication failed.';
  }
  return error.message || 'An unexpected error occurred.';
}

export default function LoginPage() {
  const { login, isLoggingIn, loginError } = useAuth();
  const [demoError, setDemoError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = (data: LoginFormData) => {
    login(data);
  };

  const onTryDemo = async () => {
    setDemoError(null);
    try {
      const config = await fetchDemoConfig();
      login({ ...submitterIdentity(config) });
    } catch {
      setDemoError('Demo workspace is currently unavailable. Please try again later.');
    }
  };

  return (
    <div className="flex min-h-dvh flex-1 flex-col justify-center px-6 py-12 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-sm">
        <h1 className="mt-10 text-center text-2xl font-bold leading-9 tracking-tight text-gray-900">
          Sign in to InvoiceIQ
        </h1>
      </div>

      <div className="mt-10 sm:mx-auto sm:w-full sm:max-w-sm">
        {loginError && (
          <ErrorAlert
            title="Authentication Failed"
            message={getLoginErrorMessage(loginError)}
          />
        )}

        <form className="space-y-6" onSubmit={handleSubmit(onSubmit)} noValidate>
          <div>
            <label htmlFor="email" className="block text-sm font-medium leading-6 text-gray-900">
              Email address
            </label>
            <div className="mt-2">
              <input
                id="email"
                type="email"
                autoComplete="email"
                aria-invalid={!!errors.email}
                aria-describedby={errors.email ? 'email-error' : undefined}
                {...register('email')}
                className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6 px-3"
              />
              {errors.email && (
                <p id="email-error" className="mt-1 text-sm text-red-600" role="alert">
                  {errors.email.message}
                </p>
              )}
            </div>
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium leading-6 text-gray-900">
              Password
            </label>
            <div className="mt-2">
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                aria-invalid={!!errors.password}
                aria-describedby={errors.password ? 'password-error' : undefined}
                {...register('password')}
                className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6 px-3"
              />
              {errors.password && (
                <p id="password-error" className="mt-1 text-sm text-red-600" role="alert">
                  {errors.password.message}
                </p>
              )}
            </div>
          </div>

          <div>
            <label htmlFor="tenantId" className="block text-sm font-medium leading-6 text-gray-900">
              Tenant ID (UUID)
            </label>
            <div className="mt-2">
              <input
                id="tenantId"
                type="text"
                autoComplete="off"
                aria-invalid={!!errors.tenantId}
                aria-describedby={errors.tenantId ? 'tenantId-error' : undefined}
                {...register('tenantId')}
                className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6 px-3"
              />
              {errors.tenantId && (
                <p id="tenantId-error" className="mt-1 text-sm text-red-600" role="alert">
                  {errors.tenantId.message}
                </p>
              )}
            </div>
          </div>

          <div>
            <button
              type="submit"
              disabled={isLoggingIn}
              aria-busy={isLoggingIn}
              className="flex w-full justify-center rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold leading-6 text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoggingIn ? <Spinner size="sm" className="text-white" /> : 'Sign in'}
            </button>
          </div>
        </form>

        <div className="mt-6">
          <button
            type="button"
            onClick={onTryDemo}
            disabled={isLoggingIn}
            className="flex w-full justify-center rounded-md bg-white px-3 py-1.5 text-sm font-semibold leading-6 text-indigo-600 shadow-sm ring-1 ring-inset ring-indigo-200 hover:bg-indigo-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Try Demo — no account needed
          </button>
          <p className="mt-2 text-center text-xs text-gray-500">
            Opens a shared demo workspace; uploads are reset periodically.
          </p>
          {demoError && (
            <p className="mt-2 text-center text-sm text-red-600" role="alert">
              {demoError}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
