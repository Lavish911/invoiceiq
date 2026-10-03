import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { workflowApi } from '@/services/workflow-service';
import type { ApprovalRequest } from '@/types/invoice';

export const workflowKeys = {
  all: ['workflows'] as const,
  detail: (invoiceId: string) => [...workflowKeys.all, invoiceId] as const,
};

export function useWorkflow(invoiceId: string) {
  return useQuery({
    queryKey: workflowKeys.detail(invoiceId),
    queryFn: () => workflowApi.getWorkflow(invoiceId),
    enabled: !!invoiceId,
    retry: false, // If workflow doesn't exist, it might 404 cleanly, don't retry aggressively.
  });
}

export function useApprove(invoiceId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: ApprovalRequest) => workflowApi.approve(invoiceId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: workflowKeys.detail(invoiceId) });
      // Invalidate invoice detail to refresh invoice status as well
      queryClient.invalidateQueries({ queryKey: ['invoices', 'detail', invoiceId] });
    },
  });
}

export function useReject(invoiceId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: ApprovalRequest) => workflowApi.reject(invoiceId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: workflowKeys.detail(invoiceId) });
      queryClient.invalidateQueries({ queryKey: ['invoices', 'detail', invoiceId] });
    },
  });
}

export function useSubmit(invoiceId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => workflowApi.submit(invoiceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: workflowKeys.detail(invoiceId) });
      queryClient.invalidateQueries({ queryKey: ['invoices', 'detail', invoiceId] });
    },
  });
}

export function useInitiate(invoiceId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => workflowApi.initiate(invoiceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: workflowKeys.detail(invoiceId) });
      queryClient.invalidateQueries({ queryKey: ['invoices', 'detail', invoiceId] });
    },
  });
}
