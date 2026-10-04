import { describe, expect, it } from 'vitest';

import { validateUploadFile } from '../utils';

describe('validateUploadFile (shared by both upload flows)', () => {
  it('accepts PDF/PNG/JPG within 10 MB', () => {
    expect(
      validateUploadFile(new File(['x'], 'a.pdf', { type: 'application/pdf' })),
    ).toBeNull();
    expect(
      validateUploadFile(new File(['x'], 'a.png', { type: 'image/png' })),
    ).toBeNull();
    expect(
      validateUploadFile(new File(['x'], 'a.jpg', { type: 'image/jpeg' })),
    ).toBeNull();
  });

  it('rejects disallowed types and oversized files', () => {
    expect(
      validateUploadFile(new File(['x'], 'a.exe', { type: 'application/octet-stream' })),
    ).not.toBeNull();
    expect(
      validateUploadFile(
        new File([new Uint8Array(11 * 1024 * 1024)], 'big.pdf', { type: 'application/pdf' }),
      ),
    ).not.toBeNull();
  });
});
