'use client';

import { useState, useRef, useCallback } from 'react';
import { UploadCloud, File, AlertCircle, CheckCircle2, Loader2, FileText, ChevronDown, ChevronUp } from 'lucide-react';
import { useUploadDocument, useDocumentExtraction } from '@/hooks/use-document';
import { cn, ALLOWED_UPLOAD_EXTENSIONS, validateUploadFile } from '@/lib/utils';
import { isAxiosError } from 'axios';

const ALLOWED_EXTENSIONS = ALLOWED_UPLOAD_EXTENSIONS;

function getUploadErrorMessage(err: unknown): string {
  if (isAxiosError(err)) {
    const status = err.response?.status;
    if (status === 413) return 'File is too large for the server. Maximum upload size may be smaller than 10 MB.';
    if (status === 403) return 'You do not have permission to upload documents to this invoice.';
    if (status === 404) return 'Invoice not found. It may have been deleted.';
    if (status === 409) return 'Conflict: this document was already processed. Please refresh and try again.';
    if (status === 401) return 'Your session has expired. Please log in again.';
    if (!err.response) return 'Network error. Please check your connection and try again.';
    return err.response.data?.message || 'Failed to upload document.';
  }
  return 'An unexpected error occurred during upload.';
}

interface DocumentUploaderProps {
  invoiceId: string;
}

export function DocumentUploader({ invoiceId }: DocumentUploaderProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [documentId, setDocumentId] = useState<string | null>(null);
  const [showRawData, setShowRawData] = useState(false);
  const [isDragOver, setIsDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const uploadMutation = useUploadDocument(invoiceId);
  const extractionQuery = useDocumentExtraction(invoiceId, documentId);

  const validateFile = useCallback((file: File): string | null => {
    return validateUploadFile(file);
  }, []);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setError(null);
    const file = e.target.files?.[0];
    if (!file) return;

    const validationError = validateFile(file);
    if (validationError) {
      setError(validationError);
      setSelectedFile(null);
      return;
    }

    setSelectedFile(file);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    setError(null);
    const file = e.dataTransfer.files?.[0];
    if (!file) return;

    const validationError = validateFile(file);
    if (validationError) {
      setError(validationError);
      setSelectedFile(null);
      return;
    }

    setSelectedFile(file);
  };

  const handleUpload = () => {
    if (!selectedFile) return;
    setError(null);
    setDocumentId(null);
    
    uploadMutation.mutate(selectedFile, {
      onSuccess: (data) => {
        setDocumentId(data.id);
      },
      onError: (err) => {
        setError(getUploadErrorMessage(err));
      }
    });
  };

  const reset = () => {
    setSelectedFile(null);
    setDocumentId(null);
    setError(null);
    setShowRawData(false);
    uploadMutation.reset();
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const isUploading = uploadMutation.isPending;
  const extraction = extractionQuery.data;
  const isExtracting = extraction?.status === 'PENDING' || extraction?.status === 'IN_PROGRESS';
  const isCompleted = extraction?.status === 'COMPLETED';
  const isFailed = extraction?.status === 'FAILED';

  return (
    <div className="rounded-lg bg-white shadow-sm ring-1 ring-gray-200">
      <div className="border-b border-gray-200 px-4 py-4 sm:px-6 flex flex-wrap justify-between items-center gap-2">
        <h3 className="text-base font-semibold leading-6 text-gray-900 flex items-center gap-2">
          <FileText className="h-5 w-5 text-indigo-600" aria-hidden="true" />
          Document &amp; Extraction
        </h3>
        {documentId && (
          <button
            onClick={reset}
            className="text-sm font-medium text-indigo-600 hover:text-indigo-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
          >
            Upload Another
          </button>
        )}
      </div>

      <div className="px-4 py-5 sm:p-6">
        {!documentId ? (
          // Upload UI
          <div className="space-y-4">
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              className={cn(
                "mt-2 flex justify-center rounded-lg border border-dashed px-6 py-10 transition-colors",
                error ? "border-red-300 bg-red-50" :
                isDragOver ? "border-indigo-400 bg-indigo-50" :
                "border-gray-900/25 bg-gray-50",
                isUploading ? "opacity-50 pointer-events-none" : ""
              )}
              role="region"
              aria-label="File upload drop zone"
            >
              <div className="text-center">
                <UploadCloud className="mx-auto h-12 w-12 text-gray-300" aria-hidden="true" />
                <div className="mt-4 flex text-sm leading-6 text-gray-600 justify-center">
                  <label
                    htmlFor="file-upload"
                    className="relative cursor-pointer rounded-md bg-transparent font-semibold text-indigo-600 focus-within:outline-none focus-within:ring-2 focus-within:ring-indigo-600 focus-within:ring-offset-2 hover:text-indigo-500"
                  >
                    <span>Upload a file</span>
                    <input
                      id="file-upload"
                      name="file-upload"
                      type="file"
                      className="sr-only"
                      ref={fileInputRef}
                      onChange={handleFileChange}
                      accept={ALLOWED_EXTENSIONS}
                      disabled={isUploading}
                      aria-describedby="file-upload-hint"
                    />
                  </label>
                  <p className="pl-1">or drag and drop</p>
                </div>
                <p id="file-upload-hint" className="text-xs leading-5 text-gray-600">
                  PDF, PNG, JPG up to 10 MB
                </p>
              </div>
            </div>

            {selectedFile && !error && (
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between rounded-md bg-indigo-50 px-4 py-3 gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <File className="h-5 w-5 text-indigo-600 shrink-0" aria-hidden="true" />
                  <div className="text-sm min-w-0">
                    <p className="font-medium text-indigo-900 truncate">{selectedFile.name}</p>
                    <p className="text-indigo-700">{(selectedFile.size / 1024 / 1024).toFixed(2)} MB</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={handleUpload}
                  disabled={isUploading}
                  aria-busy={isUploading}
                  className="rounded bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2 shrink-0"
                >
                  {isUploading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : null}
                  {isUploading ? 'Uploading…' : 'Upload'}
                </button>
              </div>
            )}

            {error && (
              <div className="rounded-md bg-red-50 p-4" role="alert" aria-live="assertive">
                <div className="flex">
                  <div className="flex-shrink-0">
                    <AlertCircle className="h-5 w-5 text-red-400" aria-hidden="true" />
                  </div>
                  <div className="ml-3">
                    <h3 className="text-sm font-medium text-red-800">Upload failed</h3>
                    <p className="mt-2 text-sm text-red-700">{error}</p>
                  </div>
                </div>
              </div>
            )}
          </div>
        ) : (
          // Processing & Results UI
          <div className="space-y-6">
            {/* Status Banner */}
            <div className={cn(
              "rounded-md p-4 border",
              isExtracting ? "bg-blue-50 border-blue-200" :
              isCompleted ? "bg-green-50 border-green-200" :
              "bg-red-50 border-red-200"
            )} role="status" aria-live="polite">
              <div className="flex items-center">
                {isExtracting ? (
                  <Loader2 className="h-5 w-5 text-blue-400 animate-spin mr-3" aria-hidden="true" />
                ) : isCompleted ? (
                  <CheckCircle2 className="h-5 w-5 text-green-400 mr-3" aria-hidden="true" />
                ) : (
                  <AlertCircle className="h-5 w-5 text-red-400 mr-3" aria-hidden="true" />
                )}
                
                <div>
                  <h3 className={cn(
                    "text-sm font-medium",
                    isExtracting ? "text-blue-800" :
                    isCompleted ? "text-green-800" :
                    "text-red-800"
                  )}>
                    {isExtracting && 'Extracting Data…'}
                    {isCompleted && 'Extraction Complete'}
                    {isFailed && 'Extraction Failed'}
                  </h3>
                  <p className={cn(
                    "mt-1 text-sm",
                    isExtracting ? "text-blue-700" :
                    isCompleted ? "text-green-700" :
                    "text-red-700"
                  )}>
                    {isExtracting && 'Our AI is analyzing your document. This usually takes 10–30 seconds.'}
                    {isCompleted && 'Successfully processed the document via OCR.'}
                    {isFailed && (extraction?.errorMessage || 'An unknown error occurred during OCR.')}
                  </p>
                </div>
              </div>
            </div>

            {/* Results Display */}
            {isCompleted && extraction?.extractedData && (
              <div className="space-y-4">
                <h4 className="font-medium text-gray-900 border-b pb-2">Extracted Information</h4>
                
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {Object.entries(extraction.extractedData).map(([key, value]) => {
                    const confidence = extraction.confidenceScores?.[key];
                    return (
                      <div key={key} className="bg-gray-50 p-3 rounded border border-gray-100">
                        <dt className="text-xs font-medium text-gray-500 uppercase tracking-wider mb-1">
                          {key.replace(/_/g, ' ')}
                        </dt>
                        <dd className="text-sm font-semibold text-gray-900 truncate" title={String(value)}>
                          {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                        </dd>
                        {confidence !== undefined && (
                          <div className="mt-2 flex items-center justify-between text-xs">
                            <span className="text-gray-500">Confidence</span>
                            <span className={cn(
                              "font-medium",
                              confidence >= 0.9 ? "text-green-600" :
                              confidence >= 0.7 ? "text-yellow-600" :
                              "text-red-600"
                            )}>
                              {Math.round(confidence * 100)}%
                            </span>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>

                <div className="mt-4 pt-4 border-t">
                  <button 
                    onClick={() => setShowRawData(!showRawData)}
                    className="flex items-center text-sm font-medium text-gray-600 hover:text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                    aria-expanded={showRawData}
                  >
                    {showRawData ? <ChevronUp className="h-4 w-4 mr-1" aria-hidden="true" /> : <ChevronDown className="h-4 w-4 mr-1" aria-hidden="true" />}
                    {showRawData ? 'Hide Raw JSON' : 'View Raw JSON'}
                  </button>
                  {showRawData && (
                    <pre className="mt-3 p-4 bg-gray-900 text-gray-100 rounded-md overflow-x-auto text-xs">
                      {JSON.stringify({
                        data: extraction.extractedData,
                        confidence: extraction.confidenceScores
                      }, null, 2)}
                    </pre>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
