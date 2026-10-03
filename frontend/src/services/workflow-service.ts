import { apiClient } from '@/lib/api-client';
import type { WorkflowInstanceResponse, ApprovalRequest } from '@/types/invoice';

export const workflowApi = {
  getWorkflow: async (invoiceId: string): Promise<WorkflowInstanceResponse> => {
    return apiClient.get(`/invoices/${invoiceId}/workflow`);
  },

  initiate: async (invoiceId: string): Promise<void> => {
    return apiClient.post(`/invoices/${invoiceId}/workflow/initiate`);
  },

  approve: async (invoiceId: string, request: ApprovalRequest): Promise<void> => {
    return apiClient.post(`/invoices/${invoiceId}/approval/approve`, request);
  },

  reject: async (invoiceId: string, request: ApprovalRequest): Promise<void> => {
    return apiClient.post(`/invoices/${invoiceId}/approval/reject`, request);
  },

  submit: async (invoiceId: string): Promise<void> => {
    return apiClient.post(`/invoices/${invoiceId}/submit`);
  },
};
