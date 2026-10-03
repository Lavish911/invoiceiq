import { useMutation, useQuery } from '@tanstack/react-query';
import { documentApi } from '@/services/document-service';

export const documentKeys = {
  all: ['documents'] as const,
  detail: (invoiceId: string, documentId: string) => [...documentKeys.all, invoiceId, documentId] as const,
  extraction: (invoiceId: string, documentId: string) => [...documentKeys.detail(invoiceId, documentId), 'extraction'] as const,
};

export function useUploadDocument(invoiceId: string) {
  return useMutation({
    mutationFn: (file: File) => documentApi.uploadDocument(invoiceId, file),
    onSuccess: () => {
      // Return id natively managed by useMutation via the mutation response.
    },
  });
}

export function useDocumentExtraction(invoiceId: string, documentId: string | null) {
  return useQuery({
    queryKey: documentKeys.extraction(invoiceId, documentId!),
    queryFn: () => documentApi.getExtractionResult(invoiceId, documentId!),
    enabled: !!documentId,
    // Poll every 2 seconds if status is PENDING or IN_PROGRESS
    refetchInterval: (query) => {
      const data = query.state.data;
      if (!data) return 2000;
      if (data.status === 'PENDING' || data.status === 'IN_PROGRESS') {
        return 2000;
      }
      return false;
    },
  });
}
