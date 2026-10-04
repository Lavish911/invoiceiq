'use client';

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { documentApi } from '@/services/document-service';
import { ALLOWED_UPLOAD_EXTENSIONS, validateUploadFile } from '@/lib/utils';
import { Spinner } from '@/components/ui/spinner';
import { Upload } from 'lucide-react';
import { isAxiosError } from 'axios';

function getUploadNewErrorMessage(err: unknown): string {
  if (isAxiosError(err)) {
    const status = err.response?.status;
    if (status === 413) return 'File is too large for the server.';
    if (status === 403) return 'You do not have permission to create invoices.';
    if (status === 401) return 'Your session has expired. Please log in again.';
    if (status === 404) return 'Upload-new flow is not enabled.';
    if (!err.response) return 'Network error. Please check your connection and try again.';
    return err.response.data?.message || 'Failed to upload invoice.';
  }
  return 'An unexpected error occurred during upload.';
}

/**
 * "+ Upload New Invoice" entry point: picks a file, creates the DRAFT invoice
 * server-side, then navigates to it where the existing extraction UI takes over.
 */
export function UploadNewInvoiceButton() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (file: File) => documentApi.uploadNewInvoice(file),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] });
      router.push(`/invoices/${data.invoiceId}`);
    },
    onError: (err) => {
      setError(getUploadNewErrorMessage(err));
    },
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setError(null);
    const file = e.target.files?.[0];
    if (!file) return;
    const validationError = validateUploadFile(file);
    if (validationError) {
      setError(validationError);
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }
    mutation.mutate(file);
  };

  return (
    <div>
      <input
        type="file"
        ref={fileInputRef}
        className="sr-only"
        accept={ALLOWED_UPLOAD_EXTENSIONS}
        onChange={handleFileChange}
        aria-label="Upload new invoice file"
      />
      <button
        type="button"
        onClick={() => {
          setError(null);
          fileInputRef.current?.click();
        }}
        disabled={mutation.isPending}
        className="inline-flex items-center gap-2 rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50"
      >
        {mutation.isPending ? <Spinner size="sm" /> : <Upload className="h-4 w-4" aria-hidden="true" />}
        {mutation.isPending ? 'Uploading…' : '+ Upload New Invoice'}
      </button>
      {error && (
        <p className="mt-2 text-sm text-red-600" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
