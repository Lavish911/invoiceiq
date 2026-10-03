import { useQuery } from '@tanstack/react-query';
import { invoiceApi, type GetInvoicesParams } from '@/services/invoice-service';

export const invoiceKeys = {
  all: ['invoices'] as const,
  lists: () => [...invoiceKeys.all, 'list'] as const,
  list: (params: GetInvoicesParams) => [...invoiceKeys.lists(), params] as const,
  detail: (id: string) => [...invoiceKeys.all, 'detail', id] as const,
};

export function useInvoices(params: GetInvoicesParams = {}) {
  return useQuery({
    queryKey: invoiceKeys.list(params),
    queryFn: () => invoiceApi.getAll(params),
  });
}

export function useInvoice(id: string) {
  return useQuery({
    queryKey: invoiceKeys.detail(id),
    queryFn: () => invoiceApi.getById(id),
    enabled: !!id,
  });
}
