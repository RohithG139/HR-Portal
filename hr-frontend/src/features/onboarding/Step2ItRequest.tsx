/**
 * @component Step2ItRequest
 * @description S3 — Step 2: IT & equipment request form (HR).
 * Shows a status badge (Not sent / Sent to IT / Completed by IT).
 * The submit button becomes "Save & continue" after the request is sent.
 * Validation messages from spec §5 (R-SRC-3).
 * @props employeeId - number - the employee being onboarded
 * @props onContinue - function - called when navigating to step 3
 * @example
 * <Step2ItRequest employeeId={42} onContinue={() => goToStep(2)} />
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getOnboarding, saveItRequest, sendItRequest } from '../../api/onboarding'
import { getItLookups } from '../../api/lookups'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'
import { Badge, Card, Field, useToast } from '../../components/index'
import { formatDate } from '../../lib/date'

interface Step2Props {
  employeeId: number
  onContinue: () => void
}

interface ItFormState {
  laptopModel: string
  keyboardLayout: string
  readyBy: string // display format dd-mm-yyyy
  deliveryMethod: string
  notes: string
  accessories: string[]
  accounts: string[]
}

const EMPTY_IT: ItFormState = {
  laptopModel: '', keyboardLayout: '', readyBy: '', deliveryMethod: '',
  notes: '', accessories: [], accounts: [],
}

function toggleItem(arr: string[], item: string): string[] {
  return arr.includes(item) ? arr.filter((x) => x !== item) : [...arr, item]
}

/** Converts display date to ISO for the API, returns empty string if blank. */
function displayToIso(display: string): string {
  if (display === '') return ''
  try {
    const [d, m, y] = display.split('-')
    if (d === undefined || m === undefined || y === undefined) return ''
    return `${y}-${m}-${d}`
  } catch {
    return ''
  }
}

/**
 * Step2ItRequest renders the IT & equipment request form.
 *
 * The IT status from the backend controls which button is shown:
 * - NOT_SENT → "Send to IT"
 * - SENT → "Save & continue" (already sent, just navigate)
 */
export function Step2ItRequest({ employeeId, onContinue }: Step2Props): ReactElement {
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const [form, setForm] = useState<ItFormState>(EMPTY_IT)
  const [errors, setErrors] = useState<Partial<Record<keyof ItFormState | '_general', string>>>({})

  const { data: summary } = useQuery({
    queryKey: ['onboarding-summary', employeeId],
    queryFn: () => getOnboarding(employeeId),
  })

  const { data: itLookups } = useQuery({
    queryKey: ['it-lookups'],
    queryFn: getItLookups,
  })

  const alreadySent = summary?.itStatus !== 'NOT_SENT'

  const saveMutation = useMutation({
    mutationFn: () => saveItRequest(employeeId, {
      laptopModel: form.laptopModel || null,
      keyboardLayout: form.keyboardLayout || null,
      readyBy: displayToIso(form.readyBy) || null,
      deliveryMethod: form.deliveryMethod || null,
      notes: form.notes || null,
      accessories: form.accessories,
      accounts: form.accounts,
    }),
    onSuccess: () => showToast('IT request saved.'),
    onError: () => showToast('Failed to save.', 'error'),
  })

  const sendMutation = useMutation({
    mutationFn: () => sendItRequest(employeeId, {
      laptopModel: form.laptopModel || null,
      keyboardLayout: form.keyboardLayout || null,
      readyBy: displayToIso(form.readyBy) || null,
      deliveryMethod: form.deliveryMethod || null,
      notes: form.notes || null,
      accessories: form.accessories,
      accounts: form.accounts,
    }),
    onSuccess: () => {
      showToast('IT request sent.')
      void queryClient.invalidateQueries({ queryKey: ['onboarding-summary', employeeId] })
      onContinue()
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED') {
        const ve = err.body as ApiValidationError
        const fieldErrs: Partial<Record<keyof ItFormState | '_general', string>> = {}
        ve.errors.forEach((e) => {
          if (e.field !== undefined && e.field !== '') {
            fieldErrs[e.field as keyof ItFormState] = e.message
          } else {
            fieldErrs._general = e.message
          }
        })
        setErrors(fieldErrs)
      } else {
        showToast('Failed to send IT request.', 'error')
      }
    },
  })

  const setField = <K extends keyof ItFormState>(key: K, value: ItFormState[K]): void => {
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  const statusBadge = alreadySent ? (
    summary?.itStatus === 'COMPLETED'
      ? <Badge label={`Completed by IT · ${summary.itSentAt !== null ? formatDate(summary.itSentAt.split('T')[0]!) : ''}`} variant="success" />
      : <Badge label={`Sent to IT · ${summary?.itSentAt !== null ? formatDate((summary?.itSentAt ?? '').split('T')[0]!) : ''} · in progress`} variant="info" />
  ) : (
    <Badge label="Not sent yet" variant="default" />
  )

  const laptopOptions = (itLookups?.laptops ?? []).map((l) => ({ value: l, label: l }))
  const kbdOptions = (itLookups?.keyboardLayouts ?? []).map((k) => ({ value: k, label: k }))
  const deliveryOptions = (itLookups?.deliveryMethods ?? []).map((d) => ({
    value: d,
    label: d === 'OFFICE_DAY1' ? 'Ready at office on day 1' : 'Ship to home address',
  }))

  return (
    <div className="space-y-6">
      {/* Laptop & hardware */}
      <Card title="Laptop & hardware" action={statusBadge}>
        <div className="space-y-4">
          {errors._general !== undefined && (
            <p role="alert" className="text-xs text-red-600">{errors._general}</p>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field
              id="laptopModel" label="Laptop" type="select"
              value={form.laptopModel}
              onChange={(v) => setField('laptopModel', String(v))}
              required={!alreadySent}
              options={laptopOptions}
              error={errors.laptopModel}
            />
            <Field
              id="readyBy" label="Ready by (dd-mm-yyyy)" type="date"
              value={form.readyBy}
              onChange={(v) => setField('readyBy', String(v))}
              required={!alreadySent}
              placeholder="dd-mm-yyyy"
              error={errors.readyBy}
            />
            <Field
              id="deliveryMethod" label="Delivery" type="select"
              value={form.deliveryMethod}
              onChange={(v) => setField('deliveryMethod', String(v))}
              required={!alreadySent}
              options={deliveryOptions}
              error={errors.deliveryMethod}
            />
            <Field
              id="keyboardLayout" label="Keyboard layout (optional)" type="select"
              value={form.keyboardLayout}
              onChange={(v) => setField('keyboardLayout', String(v))}
              options={kbdOptions}
            />
          </div>
          {/* Accessories */}
          <fieldset>
            <legend className="text-sm font-medium text-gray-700 mb-2">Accessories</legend>
            <div className="flex flex-wrap gap-3">
              {(itLookups?.accessories ?? []).map((acc) => (
                <label key={acc} className="flex items-center gap-2 text-sm cursor-pointer min-h-[44px]">
                  <input
                    type="checkbox"
                    checked={form.accessories.includes(acc)}
                    onChange={() => setField('accessories', toggleItem(form.accessories, acc))}
                    disabled={alreadySent}
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
                  />
                  {acc}
                </label>
              ))}
            </div>
          </fieldset>
        </div>
      </Card>

      {/* Accounts & software */}
      <Card title="Accounts & software">
        <div className="space-y-4">
          <fieldset>
            <legend className="sr-only">Accounts & software</legend>
            <div className="flex flex-wrap gap-3">
              {(itLookups?.accounts ?? []).map((acc) => (
                <label key={acc} className="flex items-center gap-2 text-sm cursor-pointer min-h-[44px]">
                  <input
                    type="checkbox"
                    checked={form.accounts.includes(acc)}
                    onChange={() => setField('accounts', toggleItem(form.accounts, acc))}
                    disabled={alreadySent}
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
                  />
                  {acc}
                </label>
              ))}
            </div>
          </fieldset>
          <Field
            id="notes" label="Notes for IT" type="textarea"
            value={form.notes}
            onChange={(v) => setField('notes', String(v))}
            rows={3}
            readOnly={alreadySent}
          />
        </div>
      </Card>

      {/* Action bar (R-UI-1) */}
      <div className="flex justify-between items-center pt-4 border-t border-gray-200">
        <button
          type="button"
          onClick={() => window.history.back()}
          className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-500 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors"
        >
          Cancel
        </button>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => { if (!alreadySent) saveMutation.mutate() }}
            disabled={alreadySent || saveMutation.isPending}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
          >
            Back
          </button>
          <button
            type="button"
            onClick={() => {
              if (alreadySent) { onContinue() } else { sendMutation.mutate() }
            }}
            disabled={sendMutation.isPending}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
          >
            {alreadySent ? 'Save & continue' : sendMutation.isPending ? 'Sending…' : 'Send to IT'}
          </button>
        </div>
      </div>
    </div>
  )
}
