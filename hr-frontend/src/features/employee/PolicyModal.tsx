/**
 * @component PolicyModal
 * @description S8 — Policy acknowledgement pop-up (Employee).
 * Shows the policy PDF for a checklist item and requires the employee to
 * tick "I have read and accept this policy" before confirming (spec §7).
 * Uses A26 to load the PDF URL and A27 to submit the acknowledgement.
 * @props isOpen - boolean - visibility
 * @props onClose - function - close handler
 * @props itemId - number - the checklist item id whose policy to display
 * @props onAcknowledged - function - called after successful acknowledgement
 * @example
 * <PolicyModal isOpen={open} onClose={() => setOpen(false)} itemId={5} onAcknowledged={refetch} />
 */

import { useEffect, useState } from 'react'
import type { ReactElement } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { getPolicyDocument, acknowledgePolicy } from '../../api/onboarding'
import { Modal, useToast } from '../../components/index'

interface PolicyModalProps {
  isOpen: boolean
  onClose: () => void
  itemId: number
  onAcknowledged: () => void
}

/**
 * PolicyModal loads the policy document for a checklist item and records
 * the employee's acknowledgement.
 *
 * A26 fetches the document metadata; the PDF itself is served from the
 * backend document storage. A27 accepts the acknowledgement and the
 * backend ticks the checklist item automatically.
 *
 * The spec message (R-SRC-3): "Confirm that you have read the policy."
 */
export function PolicyModal({ isOpen, onClose, itemId, onAcknowledged }: PolicyModalProps): ReactElement {
  const { showToast } = useToast()
  const [accepted, setAccepted] = useState(false)
  const [acceptError, setAcceptError] = useState<string | null>(null)

  // Load the policy PDF (as a Blob — A26 returns the file directly, not JSON)
  // when the modal is open.
  const { data: policyBlob } = useQuery({
    queryKey: ['policy-doc', itemId],
    queryFn: () => getPolicyDocument(itemId),
    enabled: isOpen,
  })

  // The iframe needs a URL, and a plain `src` to the backend endpoint would
  // load without the Authorization header (iframes can't attach one), so the
  // PDF is fetched authenticated as a Blob and turned into a local object URL.
  // Revoked whenever the blob changes or the modal closes, to avoid leaking it.
  //
  // Security: a `blob:` URL is same-origin with this page. The blob keeps
  // whatever Content-Type the server sent (`document.content_type`, which A07
  // accepts from the uploader, not from inspecting the file). If that were
  // ever `text/html` or `image/svg+xml` instead of a real PDF, an iframe would
  // render it as an active document on our own origin rather than as a file
  // to view, giving it script access to `window.parent`. Re-wrapping the blob
  // with an explicit `application/pdf` type (ignoring whatever type the
  // response claimed) forces the browser's PDF viewer path regardless, so
  // non-PDF content fails to render instead of executing.
  const [pdfUrl, setPdfUrl] = useState<string | null>(null)
  useEffect(() => {
    if (policyBlob === undefined) {
      return
    }
    const pdfBlob = new Blob([policyBlob], { type: 'application/pdf' })
    const url = URL.createObjectURL(pdfBlob)
    setPdfUrl(url)
    return () => {
      URL.revokeObjectURL(url)
    }
  }, [policyBlob])

  const mutation = useMutation({
    mutationFn: () => acknowledgePolicy(itemId, accepted),
    onSuccess: () => {
      showToast('Policy acknowledged.')
      setAccepted(false)
      setAcceptError(null)
      onAcknowledged()
      onClose()
    },
    onError: () => {
      showToast('Failed to acknowledge policy.', 'error')
    },
  })

  const handleConfirm = (): void => {
    if (!accepted) {
      // Spec §7 exact message (R-SRC-3)
      setAcceptError('Confirm that you have read the policy.')
      return
    }
    mutation.mutate()
  }

  return (
    <Modal
      isOpen={isOpen}
      title="Policy"
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-500 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={mutation.isPending}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
          >
            {mutation.isPending ? 'Confirming…' : 'Confirm'}
          </button>
        </>
      }
    >
      <div className="space-y-4">
        {/* PDF viewer — an object URL created from the authenticated A26 blob fetch */}
        {pdfUrl !== null ? (
          <div className="border border-gray-200 rounded overflow-hidden" style={{ height: 400 }}>
            <iframe
              src={pdfUrl}
              title="Policy document"
              className="w-full h-full"
              aria-label="Policy document"
            />
          </div>
        ) : (
          <div className="h-40 flex items-center justify-center text-gray-400 text-sm">
            Loading policy document…
          </div>
        )}

        {/* Acceptance checkbox */}
        <div className="space-y-1">
          <label className="flex items-center gap-3 cursor-pointer min-h-[44px]">
            <input
              type="checkbox"
              id="policy-accept"
              checked={accepted}
              onChange={(e) => {
                setAccepted(e.target.checked)
                if (e.target.checked) setAcceptError(null)
              }}
              className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
            />
            <span className="text-sm text-gray-900">
              I have read and accept this policy
            </span>
          </label>
          {acceptError !== null && (
            <p role="alert" className="text-xs text-red-600 ml-7">
              {acceptError}
            </p>
          )}
        </div>
      </div>
    </Modal>
  )
}
