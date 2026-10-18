/**
 * Pure validation helpers for the Settings feature (FRD-08 §5).
 *
 * Exported as standalone functions so they can be unit-tested independently
 * of the React component tree. All messages are copied verbatim from the spec
 * (R-SRC-3) and match what the backend already validates.
 */

import type { FormSectionDto } from '../../api/settingsAppraisalForms'
import type { TimerDto, TimerUpdateRequest } from '../../api/settingsTimers'

/**
 * Validates the CC field for e-mail templates (spec §5 S5).
 * Returns the error message if invalid, or null if valid.
 * An empty string is valid (CC is optional).
 *
 * @param cc  Comma-separated e-mail addresses entered by HR.
 */
export function validateCcField(cc: string): string | null {
  const trimmed = cc.trim()
  if (trimmed === '') return null

  // Each part must be a valid e-mail address
  const parts = trimmed.split(',').map((p) => p.trim())
  const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

  for (const part of parts) {
    if (part === '' || !emailRe.test(part)) {
      return 'Enter valid e-mail addresses, separated by commas.'
    }
  }
  return null
}

/**
 * Checks whether the given sections array satisfies the appraisal-form activation rule:
 * at least one section must exist and every section must have at least one question.
 * Returns the error message if the form cannot be activated, or null if it can.
 *
 * Used for the client-side guard on "Save form" when status is ACTIVE (spec AC-09).
 * The backend enforces this too — this is a pre-flight check to save the round-trip.
 *
 * @param sections  Sections from the current form state.
 */
export function validateFormHasSectionWithQuestion(
  sections: FormSectionDto[],
): string | null {
  if (sections.length === 0) return 'Add at least one section with a question.'
  for (const section of sections) {
    if (section.questions.length === 0) return 'Add at least one section with a question.'
  }
  return null
}

/**
 * Builds the minimal TimerUpdateRequest payload from a changed timer row.
 * Only includes fields that the backend considers writable (ADM-01).
 * Returns null if no writable field differs from the original.
 *
 * @param original  The timer as loaded from the backend.
 * @param edited    The timer as edited by the user in local state.
 */
export function buildTimerUpdate(
  original: Pick<TimerDto, 'valueInt' | 'valueText' | 'recipientRole' | 'enabled' | 'version'>,
  edited: Pick<TimerDto, 'valueInt' | 'valueText' | 'recipientRole' | 'enabled'>,
): TimerUpdateRequest | null {
  const patch: Partial<TimerUpdateRequest> = {}
  let changed = false

  if (edited.valueInt !== original.valueInt) {
    patch.valueInt = edited.valueInt
    changed = true
  }
  if (edited.valueText !== original.valueText) {
    patch.valueText = edited.valueText
    changed = true
  }
  if (edited.recipientRole !== original.recipientRole) {
    patch.recipientRole = edited.recipientRole
    changed = true
  }
  if (edited.enabled !== original.enabled) {
    patch.enabled = edited.enabled
    changed = true
  }

  // version is required by the backend on every PUT (R-DB-5, D-10), not just when something
  // else changed — but this function returns null for an unchanged row regardless, so it is
  // only ever attached to a patch that is actually being sent.
  return changed ? { ...patch, version: original.version } : null
}
