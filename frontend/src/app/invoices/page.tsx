'use client';

import { useState, useMemo } from 'react';
import Link from 'next/link';
import { useInvoices } from '@/hooks/use-invoices';
import { ProtectedRoute } from '@/components/auth/protected-route';
import { useAuth } from '@/hooks/use-auth';
import { Spinner } from '@/components/ui/spinner';
import { ErrorAlert } from '@/components/ui/error-alert';
import { cn } from '@/lib/utils';
import type { InvoiceStatus } from '@/types/invoice';
import {
  ChevronLeft,
  ChevronRight,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  RefreshCw,
  Eye,
  Search,
  Inbox,
  ArrowLeft,
} from 'lucide-react';

// Backend-allowed sort fields
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const SORT_FIELDS = [
  { key: 'invoiceNumber', label: 'Invoice #' },
  { key: 'invoiceDate', label: 'Invoice Date' },
  { key: 'dueDate', label: 'Due Date' },
  { key: 'totalAmount', label: 'Amount' },
  { key: 'createdAt', label: 'Created' },
  { key: 'updatedAt', label: 'Updated' },
] as const;

type SortField = (typeof SORT_FIELDS)[number]['key'];
type SortDirection = 'asc' | 'desc';

const STATUS_OPTIONS: { value: InvoiceStatus | ''; label: string }[] = [
  { value: '', label: 'All Statuses' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PROCESSING', label: 'Processing' },
  { value: 'NEEDS_REVIEW', label: 'Needs Review' },
  { value: 'PENDING_APPROVAL', label: 'Pending Approval' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'PAID', label: 'Paid' },
];

const STATUS_COLORS: Record<InvoiceStatus, string> = {
  DRAFT: 'bg-gray-100 text-gray-800',
  PROCESSING: 'bg-blue-100 text-blue-800',
  NEEDS_REVIEW: 'bg-yellow-100 text-yellow-800',
  PENDING_APPROVAL: 'bg-orange-100 text-orange-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
  PAID: 'bg-emerald-100 text-emerald-800',
};

const STATUS_LABELS: Record<InvoiceStatus, string> = {
  DRAFT: 'Draft',
  PROCESSING: 'Processing',
  NEEDS_REVIEW: 'Needs Review',
  PENDING_APPROVAL: 'Pending Approval',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  PAID: 'Paid',
};

const PAGE_SIZE = 20;

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
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return dateStr;
  }
}

function SortableHeader({
  field,
  label,
  currentSort,
  currentDirection,
  onSort,
}: {
  field: SortField;
  label: string;
  currentSort: SortField;
  currentDirection: SortDirection;
  onSort: (field: SortField) => void;
}) {
  const isActive = currentSort === field;
  return (
    <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
      <button
        onClick={() => onSort(field)}
        aria-label={`Sort by ${label}, currently ${isActive ? currentDirection + 'ending' : 'unsorted'}`}
        className="group inline-flex items-center gap-1 hover:text-gray-900 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 rounded"
      >
        {label}
        {isActive ? (
          currentDirection === 'asc' ? (
            <ArrowUp className="h-3.5 w-3.5 text-indigo-600" aria-hidden="true" />
          ) : (
            <ArrowDown className="h-3.5 w-3.5 text-indigo-600" aria-hidden="true" />
          )
        ) : (
          <ArrowUpDown className="h-3.5 w-3.5 text-gray-400 opacity-0 group-hover:opacity-100 transition-opacity" aria-hidden="true" />
        )}
      </button>
    </th>
  );
}

export default function InvoiceListPage() {
  const { logout } = useAuth();

  // Server-side pagination and sorting state
  const [page, setPage] = useState(0);
  const [sortField, setSortField] = useState<SortField>('createdAt');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');

  // Client-side filters (backend has no filter endpoints)
  const [statusFilter, setStatusFilter] = useState<InvoiceStatus | ''>('');
  const [searchQuery, setSearchQuery] = useState('');

  const sortParam = `${sortField},${sortDirection}`;
  const { data, isLoading, isError, error, refetch, isFetching } = useInvoices({
    page,
    size: PAGE_SIZE,
    sort: sortParam,
  });

  const handleSort = (field: SortField) => {
    if (field === sortField) {
      setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
    setPage(0);
  };

  // Client-side filtering of the already-fetched page
  const filteredInvoices = useMemo(() => {
    let results = data?.content ?? [];

    if (statusFilter) {
      results = results.filter((inv) => inv.status === statusFilter);
    }

    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      results = results.filter(
        (inv) =>
          inv.invoiceNumber?.toLowerCase().includes(q) ||
          inv.vendor?.name?.toLowerCase().includes(q)
      );
    }

    return results;
  }, [data?.content, statusFilter, searchQuery]);

  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;
  const currentPageNumber = data?.number ?? 0;
  const isFirstPage = data?.first ?? true;
  const isLastPage = data?.last ?? true;

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <header className="border-b border-gray-200 bg-white shadow-sm">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
            <div className="flex items-center gap-4">
              <Link href="/" className="text-gray-500 hover:text-gray-900 transition-colors">
                <ArrowLeft className="h-5 w-5" />
              </Link>
              <h1 className="text-xl font-bold text-gray-900">Invoices</h1>
            </div>
            <button
              onClick={logout}
              className="rounded-md bg-gray-100 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
            >
              Logout
            </button>
          </div>
        </header>

        <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          {/* Toolbar: search, filter, refresh */}
          <div className="mb-6 flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
            <div className="flex flex-col sm:flex-row gap-3 flex-1 w-full sm:w-auto">
              {/* Client-side search */}
              <div className="relative flex-1 max-w-md">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                <input
                  type="text"
                  placeholder="Search invoice # or vendor…"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  aria-label="Search invoices by number or vendor"
                  className="block w-full rounded-md border-0 py-2 pl-10 pr-3 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm"
                />
              </div>
              {/* Client-side status filter */}
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as InvoiceStatus | '')}
                aria-label="Filter by status"
                className="rounded-md border-0 py-2 pl-3 pr-8 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm"
              >
                {STATUS_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <button
              onClick={() => refetch()}
              disabled={isFetching}
              className="inline-flex items-center gap-2 rounded-md bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 disabled:opacity-50"
            >
              <RefreshCw className={cn('h-4 w-4', isFetching && 'animate-spin')} />
              Refresh
            </button>
          </div>

          {/* Content area */}
          {isLoading ? (
            <div className="flex h-64 w-full items-center justify-center">
              <Spinner size="lg" />
            </div>
          ) : isError ? (
            <div className="max-w-3xl mx-auto">
              <ErrorAlert
                title="Error loading invoices"
                message={error instanceof Error ? error.message : 'Failed to load invoices.'}
              />
              <div className="mt-4 flex justify-center">
                <button
                  onClick={() => refetch()}
                  className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
                >
                  <RefreshCw className="h-4 w-4" />
                  Retry
                </button>
              </div>
            </div>
          ) : totalElements === 0 ? (
            <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-gray-300 p-12">
              <Inbox className="h-12 w-12 text-gray-400" />
              <h3 className="mt-4 text-lg font-medium text-gray-900">No invoices</h3>
              <p className="mt-1 text-sm text-gray-500">No invoices have been created yet.</p>
            </div>
          ) : (
            <>
              {/* Results info */}
              <div className="mb-2 text-sm text-gray-500">
                {filteredInvoices.length < (data?.content.length ?? 0) ? (
                  <span>
                    Showing {filteredInvoices.length} filtered result(s) from page {currentPageNumber + 1} of {totalPages} ({totalElements} total)
                  </span>
                ) : (
                  <span>
                    Page {currentPageNumber + 1} of {totalPages} ({totalElements} total invoices)
                  </span>
                )}
              </div>

              {/* Table */}
              <div className="overflow-x-auto rounded-lg border border-gray-200 shadow-sm bg-white">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <SortableHeader field="invoiceNumber" label="Invoice #" currentSort={sortField} currentDirection={sortDirection} onSort={handleSort} />
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Vendor</th>
                      <SortableHeader field="totalAmount" label="Amount" currentSort={sortField} currentDirection={sortDirection} onSort={handleSort} />
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Currency</th>
                      <SortableHeader field="invoiceDate" label="Date" currentSort={sortField} currentDirection={sortDirection} onSort={handleSort} />
                      <SortableHeader field="dueDate" label="Due Date" currentSort={sortField} currentDirection={sortDirection} onSort={handleSort} />
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Status</th>
                      <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 bg-white">
                    {filteredInvoices.length === 0 ? (
                      <tr>
                        <td colSpan={8} className="px-6 py-12 text-center text-sm text-gray-500">
                          No invoices match your filters on this page.
                        </td>
                      </tr>
                    ) : (
                      filteredInvoices.map((invoice) => (
                        <tr key={invoice.id} className="hover:bg-gray-50 transition-colors">
                          <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-indigo-600">
                            <Link href={`/invoices/${invoice.id}`} className="hover:underline">
                              {invoice.invoiceNumber || '—'}
                            </Link>
                          </td>
                          <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                            {invoice.vendor?.name ?? '—'}
                          </td>
                          <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900">
                            {formatCurrency(invoice.totalAmount, invoice.currency)}
                          </td>
                          <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                            {invoice.currency}
                          </td>
                          <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                            {formatDate(invoice.invoiceDate)}
                          </td>
                          <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                            {formatDate(invoice.dueDate)}
                          </td>
                          <td className="whitespace-nowrap px-6 py-4 text-sm">
                            <span
                              className={cn(
                                'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                                STATUS_COLORS[invoice.status]
                              )}
                            >
                              {STATUS_LABELS[invoice.status]}
                            </span>
                          </td>
                          <td className="whitespace-nowrap px-6 py-4 text-right text-sm">
                            <Link
                              href={`/invoices/${invoice.id}`}
                              className="inline-flex items-center gap-1 text-indigo-600 hover:text-indigo-900 font-medium"
                            >
                              <Eye className="h-4 w-4" />
                              View
                            </Link>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              {/* Pagination controls */}
              <nav aria-label="Invoice list pagination" className="mt-4 flex flex-col sm:flex-row items-center justify-between gap-3">
                <p className="text-sm text-gray-500">
                  Showing {currentPageNumber * PAGE_SIZE + 1}–{Math.min((currentPageNumber + 1) * PAGE_SIZE, totalElements)} of {totalElements}
                </p>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={isFirstPage}
                    aria-label="Go to previous page"
                    className="inline-flex items-center gap-1 rounded-md bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                    Previous
                  </button>
                  <button
                    onClick={() => setPage((p) => p + 1)}
                    disabled={isLastPage}
                    aria-label="Go to next page"
                    className="inline-flex items-center gap-1 rounded-md bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    Next
                    <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </button>
                </div>
              </nav>
            </>
          )}
        </main>
      </div>
    </ProtectedRoute>
  );
}
