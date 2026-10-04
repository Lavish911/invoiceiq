import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

// Shared upload validation: mirrors backend DocumentService rules
// (PDF/PNG/JPG/JPEG, 10 MB). Used by both the invoice-detail uploader
// and the dashboard "+ Upload New Invoice" flow.
export const MAX_UPLOAD_FILE_SIZE = 10 * 1024 * 1024;
export const ALLOWED_UPLOAD_TYPES = [
  'application/pdf',
  'image/png',
  'image/jpeg',
  'image/jpg',
];
export const ALLOWED_UPLOAD_EXTENSIONS = '.pdf,.png,.jpg,.jpeg';

export function validateUploadFile(file: File): string | null {
  if (!ALLOWED_UPLOAD_TYPES.includes(file.type)) {
    return 'Invalid file type. Only PDF, PNG, and JPG/JPEG are supported.';
  }
  if (file.size > MAX_UPLOAD_FILE_SIZE) {
    return 'File is too large. Maximum size is 10 MB.';
  }
  return null;
}
