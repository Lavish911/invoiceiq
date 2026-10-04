'use client';

import { useInvoices } from '@/hooks/use-invoices';
import { Spinner } from '@/components/ui/spinner';
import { ErrorAlert } from '@/components/ui/error-alert';
import type { Invoice, InvoiceStatus } from '@/types/invoice';
import { cn } from '@/lib/utils';
import {
  FileText,
  Clock,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Loader,
  Banknote,
  Inbox,
} from 'lucide-react';

const STATUS_CONFIG: Record<InvoiceStatus, { label: string; color: string; icon: React.ElementType }> = {
  DRAFT: { label: 'Draft', color: 'bg-gray-100 text-gray-800', icon: FileText },
  PROCESSING: { label: 'Processing', color: 'bg-blue-100 text-blue-800', icon: Loader },
  NEEDS_REVIEW: { label: 'Needs Review', color: 'bg-yellow-100 text-yellow-800', icon: AlertTriangle },
  PENDING_APPROVAL: { label: 'Pending Approval', color: 'bg-orange-100 text-orange-800', icon: Clock },
  APPROVED: { label: 'Approved', color: 'bg-green-100 text-green-800', icon: CheckCircle },
  REJECTED: { label: 'Rejected', color: 'bg-red-100 text-red-800', icon: XCircle },
  PAID: { label: 'Paid', color: 'bg-emerald-100 text-emerald-800', icon: Banknote },
};

function StatusBadge({ status }: { status: InvoiceStatus }) {
  const config = STATUS_CONFIG[status];
  return (
    <span className={cn('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', config.color)}>
      {config.label}
    </span>
  );
}

interface StatusCardProps {
  label: string;
  count: number;
  icon: React.ElementType;
  color: string;
}

function StatusCard({ label, count, icon: Icon, color }: StatusCardProps) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <div className="flex items-center gap-4">
        <div className={cn('rounded-full p-3', color)}>
          <Icon className="h-6 w-6" />
        </div>
        <div>
          <p className="text-sm font-medium text-gray-500">{label}</p>
          <p className="text-2xl font-bold text-gray-900">{count}</p>
        </div>
      </div>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-gray-300 p-12">
      <Inbox className="h-12 w-12 text-gray-400" />
      <h3 className="mt-4 text-lg font-medium text-gray-900">No invoices</h3>
      <p className="mt-1 text-sm text-gray-500">No invoices have been created yet.</p>
    </div>
  );
}

function computeStatusCounts(invoices: Invoice[]) {
  const counts: Record<InvoiceStatus, number> = {
    DRAFT: 0,
    PROCESSING: 0,
    NEEDS_REVIEW: 0,
    PENDING_APPROVAL: 0,
    APPROVED: 0,
    REJECTED: 0,
    PAID: 0,
  };
  for (const inv of invoices) {
    if (inv.status in counts) {
      counts[inv.status]++;
    }
  }
  return counts;
}

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

import Link from 'next/link';
import { DemoBanner } from '@/components/demo/DemoBanner';
import { UploadNewInvoiceButton } from '@/components/invoice/upload-new-invoice-button';

export function Dashboard() {
  const { data, isLoading, isError, error } = useInvoices({ page: 0, size: 100, sort: 'createdAt,desc' });

  if (isLoading) {
    return (
      <div className="flex h-64 w-full items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  if (isError) {
    const errMsg = error instanceof Error ? error.message : 'Failed to load invoices. Please try again.';
    return <ErrorAlert title="Error loading dashboard" message={errMsg} />;
  }

  const invoices = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;

  if (totalElements === 0) {
    return <EmptyState />;
  }

  const counts = computeStatusCounts(invoices);
  const recentInvoices = invoices.slice(0, 10);

  return (
    <div className="space-y-8">
      <DemoBanner />
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-gray-900">Overview</h2>
        <UploadNewInvoiceButton />
      </div>
      <div>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          <StatusCard label="Total Invoices" count={totalElements} icon={FileText} color="bg-indigo-100 text-indigo-600" />
          <StatusCard label="Draft" count={counts.DRAFT} icon={STATUS_CONFIG.DRAFT.icon} color="bg-gray-100 text-gray-600" />
          <StatusCard label="Processing" count={counts.PROCESSING} icon={STATUS_CONFIG.PROCESSING.icon} color="bg-blue-100 text-blue-600" />
          <StatusCard label="Needs Review" count={counts.NEEDS_REVIEW} icon={STATUS_CONFIG.NEEDS_REVIEW.icon} color="bg-yellow-100 text-yellow-600" />
          <StatusCard label="Pending Approval" count={counts.PENDING_APPROVAL} icon={STATUS_CONFIG.PENDING_APPROVAL.icon} color="bg-orange-100 text-orange-600" />
          <StatusCard label="Approved" count={counts.APPROVED} icon={STATUS_CONFIG.APPROVED.icon} color="bg-green-100 text-green-600" />
          <StatusCard label="Rejected" count={counts.REJECTED} icon={STATUS_CONFIG.REJECTED.icon} color="bg-red-100 text-red-600" />
          <StatusCard label="Paid" count={counts.PAID} icon={STATUS_CONFIG.PAID.icon} color="bg-emerald-100 text-emerald-600" />
        </div>
      </div>

      <div>
        <h2 className="mb-4 text-lg font-semibold text-gray-900">Recent Invoices</h2>
        <div className="overflow-x-auto rounded-lg border border-gray-200 shadow-sm">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Invoice #</th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Vendor</th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Amount</th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Date</th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Due Date</th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 bg-white">
              {recentInvoices.map((invoice) => (
                <tr key={invoice.id} className="hover:bg-gray-50 transition-colors relative group">
                  <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-indigo-600">
                    <Link href={`/invoices/${invoice.id}`} className="hover:underline focus:outline-none">
                      <span className="absolute inset-0" aria-hidden="true" />
                      {invoice.invoiceNumber || 'Unknown'}
                    </Link>
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                    {invoice.vendor?.name ?? '—'}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900">
                    {formatCurrency(invoice.totalAmount, invoice.currency)}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                    {formatDate(invoice.invoiceDate)}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                    {formatDate(invoice.dueDate)}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm">
                    <StatusBadge status={invoice.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
