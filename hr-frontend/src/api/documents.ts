/**
 * API module for document uploads (A07 `POST /api/documents`, DocumentController).
 */

import { apiUpload } from './client'

/** Document types HR may upload through A07 (DocumentService.UPLOADABLE). */
export type UploadDocType = 'CONTRACT' | 'POLICY'

/**
 * A07 — Uploads a file; the backend checks the type from the file's own bytes
 * (PDF, or PNG/JPEG for a contract) and returns the new document's id.
 */
export function uploadDocument(file: File, docType: UploadDocType): Promise<{ documentId: number }> {
  const form = new FormData()
  form.append('file', file)
  form.append('docType', docType)
  return apiUpload<{ documentId: number }>('/documents', form)
}
