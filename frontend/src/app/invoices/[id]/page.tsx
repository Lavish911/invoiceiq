'use client';

import { useParams, useRouter } from 'next/navigation';
import { ProtectedRoute } from '@/components/auth/protected-route';
import { useInvoice } from '@/hooks/use-invoices';
import { useAuth } from '@/hooks/use-auth';
import { Spinner } from '@/components/ui/spinner';
import { ErrorAlert } from '@/components/ui/error-alert';
import { ArrowLeft, FileText, Building } from 'lucide-react';
import Link from 'next/link';
import { cn } from '@/lib/utils';
import type { InvoiceStatus } from '@/types/invoice';
import { isAxiosError } from 'axios';
import { DocumentUploader } from '@/components/invoice/document-uploader';
import { ApprovalWorkflow } from '@/components/invoice/approval-workflow';
import { useSubmit, useInitiate } from '@/hooks/use-workflow';
import { Send, Play } from 'lucide-react';

const STATUS_CONFIG: Record<InvoiceStatus, { label: string; color: string }> = {
  DRAFT: { label: 'Draft', color: 'bg-gray-100 text-gray-800' },
  PROCESSING: { label: 'Processing', color: 'bg-blue-100 text-blue-800' },
  NEEDS_REVIEW: { label: 'Needs Review', color: 'bg-yellow-100 text-yellow-800' },
  PENDING_APPROVAL: { label: 'Pending Approval', color: 'bg-orange-100 text-orange-800' },
  APPROVED: { label: 'Approved', color: 'bg-green-100 text-green-800' },
  REJECTED: { label: 'Rejected', color: 'bg-red-100 text-red-800' },
  PAID: { label: 'Paid', color: 'bg-emerald-100 text-emerald-800' },
};

function formatCurrency(amount: number, currency: string) {
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

function formatDate(dateStr: string) {
  try {
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  } catch {
    return dateStr;
  }
}

function SubmitButton({ invoiceId }: { invoiceId: string }) {
  const submitMutation = useSubmit(invoiceId);

  const handleSubmit = () => {
    if (confirm('Are you sure you want to submit this invoice for workflow processing?')) {
      submitMutation.mutate(undefined, {
        onError: (err) => {
          let msg = 'Failed to submit invoice.';
          if (isAxiosError(err)) {
            if (err.response?.status === 409) msg = 'Conflict: Please refresh the page.';
            else if (err.response?.status === 403) msg = 'Permission denied.';
            else msg = err.response?.data?.message || msg;
          }
          alert(msg);
        }
      });
    }
  };

  return (
    <button
      onClick={handleSubmit}
      disabled={submitMutation.isPending}
      className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50"
    >
      {submitMutation.isPending ? <Spinner size="sm" /> : <Send className="h-4 w-4" />}
      Submit Invoice
    </button>
  );
}

function InitiateButton({ invoiceId }: { invoiceId: string }) {
  const initiateMutation = useInitiate(invoiceId);

  const handleInitiate = () => {
    if (confirm('Start the approval workflow for this invoice?')) {
      initiateMutation.mutate(undefined, {
        onError: (err) => {
          let msg = 'Failed to initiate workflow.';
          if (isAxiosError(err)) {
            if (err.response?.status === 409) msg = 'Conflict: Please refresh the page.';
            else if (err.response?.status === 403) msg = 'Permission denied.';
            else msg = err.response?.data?.message || msg;
          }
          alert(msg);
        }
      });
    }
  };

  return (
    <button
      onClick={handleInitiate}
      disabled={initiateMutation.isPending}
      className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50"
    >
      {initiateMutation.isPending ? <Spinner size="sm" /> : <Play className="h-4 w-4" />}
      Initiate Workflow
    </button>
  );
}

export default function InvoiceDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { logout } = useAuth();
  const { data: invoice, isLoading, isError, error } = useInvoice(id as string);

  let errorMessage = 'Failed to load invoice details.';
  if (isError) {
    if (isAxiosError(error)) {
      const status = error.response?.status;
      if (status === 404) {
        errorMessage = 'Invoice not found.';
      } else if (status === 403) {
        errorMessage = 'You do not have permission to view this invoice.';
      } else if (status === 409) {
        errorMessage = 'Conflict accessing invoice data. Please refresh and try again.';
      } else if (status === 401) {
        errorMessage = 'Your session has expired. Please log in again.';
      } else if (status === 413) {
        errorMessage = 'Request too large.';
      } else if (!error.response) {
        errorMessage = 'Network error. Please check your connection.';
      } else {
        errorMessage = error.response?.data?.message || error.message;
      }
    } else if (error instanceof Error) {
      errorMessage = error.message;
    }
  }

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50 pb-12">
        {/* Header */}
        <header className="border-b border-gray-200 bg-white shadow-sm">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
            <div className="flex items-center gap-4">
              <Link href="/" aria-label="Back to dashboard" className="text-gray-500 hover:text-gray-900 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 rounded">
                <ArrowLeft className="h-5 w-5" aria-hidden="true" />
              </Link>
              <h1 className="text-xl font-bold text-gray-900">Invoice Details</h1>
            </div>
            <button
              onClick={logout}
              className="rounded-md bg-gray-100 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
            >
              Logout
            </button>
          </div>
        </header>

        {/* Main content */}
        <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          {isLoading ? (
            <div className="flex h-64 w-full items-center justify-center">
              <Spinner size="lg" />
            </div>
          ) : isError ? (
            <div className="mx-auto max-w-3xl">
              <ErrorAlert title="Error" message={errorMessage} />
              <div className="mt-4 flex justify-center">
                <button
                  onClick={() => router.push('/')}
                  className="text-indigo-600 hover:text-indigo-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 rounded"
                >
                  Return to Dashboard
                </button>
              </div>
            </div>
          ) : !invoice ? (
            <div className="mx-auto max-w-3xl text-center py-12 bg-white rounded-lg border border-gray-200 shadow-sm">
              <FileText className="mx-auto h-12 w-12 text-gray-400" />
              <h3 className="mt-2 text-sm font-semibold text-gray-900">No data found</h3>
              <p className="mt-1 text-sm text-gray-500">The requested invoice could not be found.</p>
            </div>
          ) : (
            <div className="space-y-6">
              {/* Header Card */}
              <div className="overflow-hidden rounded-lg bg-white shadow-sm ring-1 ring-gray-200">
                <div className="border-b border-gray-200 px-4 py-5 sm:px-6 flex justify-between items-center flex-wrap gap-4">
                  <div>
                    <h3 className="text-base font-semibold leading-6 text-gray-900">
                      Invoice #{invoice.invoiceNumber || 'Unknown'}
                    </h3>
                    <p className="mt-1 max-w-2xl text-sm text-gray-500">
                      ID: {invoice.id}
                      {invoice.version !== undefined && ` • Version: ${invoice.version}`}
                    </p>
                  </div>
                  <div className="flex items-center gap-4">
                    {invoice.status === 'DRAFT' && (
                      <SubmitButton invoiceId={invoice.id} />
                    )}
                    {invoice.status === 'NEEDS_REVIEW' && (
                      <InitiateButton invoiceId={invoice.id} />
                    )}
                    <span
                      className={cn(
                        'inline-flex items-center rounded-full px-3 py-1 text-sm font-medium',
                        STATUS_CONFIG[invoice.status].color
                      )}
                    >
                      {STATUS_CONFIG[invoice.status].label}
                    </span>
                  </div>
                </div>
                
                <div className="px-4 py-5 sm:p-6 grid grid-cols-1 md:grid-cols-2 gap-8">
                  {/* Vendor Details */}
                  <div>
                    <h4 className="text-sm font-medium text-gray-500 mb-3 flex items-center gap-2">
                      <Building className="h-4 w-4" aria-hidden="true" /> Vendor Information
                    </h4>
                    {invoice.vendor ? (
                      <dl className="space-y-3">
                        <div className="flex justify-between">
                          <dt className="text-sm text-gray-500">Name:</dt>
                          <dd className="text-sm font-medium text-gray-900">{invoice.vendor.name}</dd>
                        </div>
                        <div className="flex justify-between">
                          <dt className="text-sm text-gray-500">Tax ID:</dt>
                          <dd className="text-sm font-medium text-gray-900">{invoice.vendor.taxId}</dd>
                        </div>
                        <div className="flex justify-between">
                          <dt className="text-sm text-gray-500">Address:</dt>
                          <dd className="text-sm font-medium text-gray-900 text-right max-w-[200px] truncate" title={invoice.vendor.address || undefined}>{invoice.vendor.address}</dd>
                        </div>
                      </dl>
                    ) : (
                      <p className="text-sm text-gray-500 italic">No vendor information available.</p>
                    )}
                  </div>

                  {/* Invoice Details */}
                  <div>
                    <h4 className="text-sm font-medium text-gray-500 mb-3 flex items-center gap-2">
                      <FileText className="h-4 w-4" aria-hidden="true" /> Invoice Summary
                    </h4>
                    <dl className="space-y-3">
                      <div className="flex justify-between">
                        <dt className="text-sm text-gray-500">Date:</dt>
                        <dd className="text-sm font-medium text-gray-900">{formatDate(invoice.invoiceDate)}</dd>
                      </div>
                      <div className="flex justify-between">
                        <dt className="text-sm text-gray-500">Due Date:</dt>
                        <dd className="text-sm font-medium text-gray-900">{formatDate(invoice.dueDate)}</dd>
                      </div>
                      <div className="flex justify-between">
                        <dt className="text-sm text-gray-500">Currency:</dt>
                        <dd className="text-sm font-medium text-gray-900">{invoice.currency}</dd>
                      </div>
                    </dl>
                  </div>
                </div>
              </div>

              {/* Line Items Table */}
              <div className="overflow-hidden rounded-lg bg-white shadow-sm ring-1 ring-gray-200">
                <div className="border-b border-gray-200 px-4 py-4 sm:px-6">
                  <h3 className="text-base font-semibold leading-6 text-gray-900">Line Items</h3>
                </div>
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Description</th>
                        <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">Quantity</th>
                        <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">Unit Price</th>
                        <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">Total Price</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200 bg-white">
                      {invoice.lineItems && invoice.lineItems.length > 0 ? (
                        invoice.lineItems.map((item) => (
                          <tr key={item.id} className="hover:bg-gray-50">
                            <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900">{item.description}</td>
                            <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500 text-right">{item.quantity}</td>
                            <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500 text-right">
                              {formatCurrency(item.unitPrice, invoice.currency)}
                            </td>
                            <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-gray-900 text-right">
                              {formatCurrency(item.totalPrice, invoice.currency)}
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={4} className="px-6 py-8 text-center text-sm text-gray-500">
                            No line items found.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
                
                {/* Totals */}
                <div className="border-t border-gray-200 bg-gray-50 px-4 py-5 sm:px-6 flex flex-col items-end">
                  <dl className="space-y-2 text-sm w-full sm:w-64">
                    <div className="flex justify-between text-gray-500">
                      <dt>Subtotal</dt>
                      <dd>{formatCurrency(invoice.totalAmount - invoice.taxAmount, invoice.currency)}</dd>
                    </div>
                    <div className="flex justify-between text-gray-500">
                      <dt>Tax</dt>
                      <dd>{formatCurrency(invoice.taxAmount, invoice.currency)}</dd>
                    </div>
                    <div className="flex justify-between font-bold text-gray-900 border-t border-gray-200 pt-2 mt-2">
                      <dt>Total Amount</dt>
                      <dd className="text-lg">{formatCurrency(invoice.totalAmount, invoice.currency)}</dd>
                    </div>
                  </dl>
                </div>
              </div>

              {/* Document Uploader */}
              <DocumentUploader invoiceId={invoice.id} />
              
              {/* Approval Workflow */}
              <ApprovalWorkflow invoiceId={invoice.id} />
            </div>
          )}
        </main>
      </div>
    </ProtectedRoute>
  );
}
