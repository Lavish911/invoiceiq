import { apiClient } from '@/lib/api-client';
import type { CreateInvoiceRequest, Invoice, Page } from '@/types/invoice';

export interface GetInvoicesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export const invoiceApi = {
  getAll: async (params: GetInvoicesParams = {}): Promise<Page<Invoice>> => {
    const { page = 0, size = 100, sort = 'createdAt,desc' } = params;
    return apiClient.get('/invoices', {
      params: { page, size, sort },
    });
  },
  getById: async (id: string): Promise<Invoice> => {
    return apiClient.get(`/invoices/${id}`);
  },
  create: async (data: CreateInvoiceRequest): Promise<Invoice> => {
    return apiClient.post('/invoices', data);
  },
};
