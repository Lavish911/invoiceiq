// Matches com.invoiceiq.invoice.entity.InvoiceStatus
export type InvoiceStatus =
  | 'DRAFT'
  | 'PROCESSING'
  | 'NEEDS_REVIEW'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'PAID';

// Matches com.invoiceiq.vendor.dto.VendorResponse
export interface Vendor {
  id: string;
  name: string;
  taxId: string | null;
  address: string | null;
  bankDetails: string | null;
}

// Matches com.invoiceiq.invoice.dto.InvoiceLineItemResponse
export interface InvoiceLineItem {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

// Matches com.invoiceiq.invoice.dto.InvoiceResponse
export interface Invoice {
  id: string;
  invoiceNumber: string;
  vendor: Vendor;
  submitterId: string;
  status: InvoiceStatus;
  totalAmount: number;
  taxAmount: number;
  currency: string;
  invoiceDate: string;   // LocalDate serialized as ISO string
  dueDate: string;       // LocalDate serialized as ISO string
  s3Key: string;
  version: number;
  createdAt: string;     // OffsetDateTime serialized as ISO string
  updatedAt: string;     // OffsetDateTime serialized as ISO string
  lineItems: InvoiceLineItem[];
}

// Matches com.invoiceiq.invoice.dto.InvoiceLineItemRequest
export interface CreateInvoiceLineItem {
  description: string;
  quantity: number;
  unitPrice: number;
}

// Matches com.invoiceiq.invoice.dto.CreateInvoiceRequest
export interface CreateInvoiceRequest {
  vendorId: string;
  invoiceNumber: string;
  invoiceDate: string;   // ISO date
  dueDate: string;       // ISO date
  currency: string;
  lineItems: CreateInvoiceLineItem[];
}

// Spring Data Page<T> shape
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;       // current page (0-indexed)
  first: boolean;
  last: boolean;
  empty: boolean;
}

export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'EXTRACTION_COMPLETED' | 'EXTRACTION_FAILED';
export type ExtractionStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';

export interface UploadDocumentResponse {
  id: string;
  status: DocumentStatus;
  message: string;
}

export interface DocumentResponse {
  id: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  storageKey: string;
  checksum: string;
  status: DocumentStatus;
  version: number;
}

export interface ExtractionResult {
  id: string;
  status: ExtractionStatus;
  extractedData: Record<string, unknown> | null;
  confidenceScores: Record<string, number> | null;
  errorMessage: string | null;
  version: number;
}

export type WorkflowStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
export type ApprovalStepStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ApprovalStepResponse {
  id: string;
  stepNumber: number;
  approverRole: string;
  status: ApprovalStepStatus;
  actedBy: string | null;
  actedAt: string | null;
  comment: string | null;
}

export interface WorkflowInstanceResponse {
  id: string;
  tenantId: string;
  invoiceId: string;
  status: WorkflowStatus;
  currentStep: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  approvalSteps: ApprovalStepResponse[];
}

export interface ApprovalRequest {
  comment: string;
  expectedVersion: number;
}
