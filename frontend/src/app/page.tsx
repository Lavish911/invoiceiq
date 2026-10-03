'use client';

import { ProtectedRoute } from '@/components/auth/protected-route';
import { Dashboard } from '@/components/dashboard/dashboard';
import { useAuth } from '@/hooks/use-auth';
import Link from 'next/link';

export default function Home() {
  const { logout } = useAuth();

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <header className="border-b border-gray-200 bg-white shadow-sm">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
            <h1 className="text-xl font-bold text-gray-900">InvoiceIQ</h1>
            <nav className="flex items-center gap-4" aria-label="Main navigation">
              <Link href="/invoices" className="text-sm font-medium text-gray-600 hover:text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 rounded">
                Invoices
              </Link>
              <button
                onClick={logout}
                className="rounded-md bg-gray-100 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
              >
                Logout
              </button>
            </nav>
          </div>
        </header>

        {/* Main content */}
        <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <Dashboard />
        </main>
      </div>
    </ProtectedRoute>
  );
}
