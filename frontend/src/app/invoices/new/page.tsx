'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ProtectedRoute } from '@/components/auth/protected-route';
import { invoiceApi } from '@/services/invoice-service';
import { ArrowLeft } from 'lucide-react';
import Link from 'next/link';
import { isAxiosError } from 'axios';

export default function CreateInvoicePage() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError(null);

    const formData = new FormData(e.currentTarget);
    const data = {
      vendorId: formData.get('vendorId') as string,
      invoiceNumber: formData.get('invoiceNumber') as string,
      invoiceDate: formData.get('invoiceDate') as string,
      dueDate: formData.get('dueDate') as string,
      currency: formData.get('currency') as string,
      lineItems: [
        {
          description: formData.get('description') as string,
          quantity: parseFloat(formData.get('quantity') as string),
          unitPrice: parseFloat(formData.get('unitPrice') as string),
        },
      ],
    };

    try {
      const response = await invoiceApi.create(data);
      router.push(`/invoices/${response.id}`);
    } catch (err: unknown) {
      setError(
        (isAxiosError(err) && (err.response?.data as { message?: string } | undefined)?.message) ||
          (err instanceof Error && err.message) ||
          'Failed to create invoice',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50 pb-12">
        <header className="border-b border-gray-200 bg-white shadow-sm">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
            <div className="flex items-center gap-4">
              <Link href="/invoices" className="text-gray-500 hover:text-gray-900 transition-colors">
                <ArrowLeft className="h-5 w-5" />
              </Link>
              <h1 className="text-xl font-bold text-gray-900">Create New Invoice</h1>
            </div>
          </div>
        </header>

        <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6 lg:px-8">
          {error && (
            <div className="mb-4 rounded-md bg-red-50 p-4 border border-red-200">
              <p className="text-sm text-red-700">{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit} className="bg-white shadow-sm ring-1 ring-gray-200 sm:rounded-xl p-6">
            <div className="space-y-6">
              <div>
                <label htmlFor="vendorId" className="block text-sm font-medium leading-6 text-gray-900">Vendor ID (UUID)</label>
                <div className="mt-2">
                  <input type="text" name="vendorId" id="vendorId" required className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="invoiceNumber" className="block text-sm font-medium leading-6 text-gray-900">Invoice Number</label>
                  <div className="mt-2">
                    <input type="text" name="invoiceNumber" id="invoiceNumber" required className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" />
                  </div>
                </div>
                <div>
                  <label htmlFor="currency" className="block text-sm font-medium leading-6 text-gray-900">Currency</label>
                  <div className="mt-2">
                    <input type="text" name="currency" id="currency" defaultValue="USD" required className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" />
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="invoiceDate" className="block text-sm font-medium leading-6 text-gray-900">Invoice Date</label>
                  <div className="mt-2">
                    <input type="date" name="invoiceDate" id="invoiceDate" required className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" />
                  </div>
                </div>
                <div>
                  <label htmlFor="dueDate" className="block text-sm font-medium leading-6 text-gray-900">Due Date</label>
                  <div className="mt-2">
                    <input type="date" name="dueDate" id="dueDate" required className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" />
                  </div>
                </div>
              </div>

              <hr className="my-6 border-gray-200" />
              <h3 className="text-lg font-medium leading-6 text-gray-900">Line Item</h3>

              <div>
                <label htmlFor="description" className="block text-sm font-medium leading-6 text-gray-900">Description</label>
                <div className="mt-2">
                  <input type="text" name="description" id="description" required className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="quantity" className="block text-sm font-medium leading-6 text-gray-900">Quantity</label>
                  <div className="mt-2">
                    <input type="number" step="0.01" name="quantity" id="quantity" required className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" />
                  </div>
                </div>
                <div>
                  <label htmlFor="unitPrice" className="block text-sm font-medium leading-6 text-gray-900">Unit Price</label>
                  <div className="mt-2">
                    <input type="number" step="0.01" name="unitPrice" id="unitPrice" required className="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" />
                  </div>
                </div>
              </div>

            </div>

            <div className="mt-8 flex items-center justify-end gap-x-6">
              <Link href="/invoices" className="text-sm font-semibold leading-6 text-gray-900 hover:text-gray-700">Cancel</Link>
              <button
                type="submit"
                disabled={isSubmitting}
                className="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50"
              >
                {isSubmitting ? 'Creating...' : 'Create Invoice'}
              </button>
            </div>
          </form>
        </main>
      </div>
    </ProtectedRoute>
  );
}
