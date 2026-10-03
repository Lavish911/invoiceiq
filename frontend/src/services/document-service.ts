import { apiClient } from '@/lib/api-client';
import { UploadDocumentResponse, DocumentResponse, ExtractionResult } from '@/types/invoice';

export const documentApi = {
  uploadDocument: async (invoiceId: string, file: File): Promise<UploadDocumentResponse> => {
    const formData = new FormData();
    formData.append('file', file);

    // Do NOT set Content-Type here: the axios instance defaults to
    // application/json (which would JSON-stringify the FormData), and a
    // manual multipart/form-data lacks the boundary. Explicit undefined
    // removes the default so the browser sets multipart/form-data with
    // the correct boundary.
    return apiClient.post(`/invoices/${invoiceId}/documents`, formData, {
      headers: {
        'Content-Type': undefined as unknown as string,
      },
    });
  },

  getDocument: async (invoiceId: string, documentId: string): Promise<DocumentResponse> => {
    return apiClient.get(`/invoices/${invoiceId}/documents/${documentId}`);
  },

  getExtractionResult: async (invoiceId: string, documentId: string): Promise<ExtractionResult> => {
    return apiClient.get(`/invoices/${invoiceId}/documents/${documentId}/extraction`);
  },
};
