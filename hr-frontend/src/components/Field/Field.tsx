/**
 * @component Field
 * @description Floating-label form field. Supports text, email, tel, date, number, select, textarea, checkbox.
 * Renders an explicit <label> tied to the input via htmlFor (accessibility requirement).
 * @props id - string - unique id (required for label association)
 * @props label - string - field label text
 * @props type - string - input type or 'select'|'textarea'|'checkbox'
 * @props value - string|number|boolean - current value
 * @props onChange - function - change handler
 * @props required - boolean - marks field as required
 * @props disabled - boolean - disables the field
 * @props readOnly - boolean - renders as read-only
 * @props error - string - validation error message (shown below the field)
 * @props options - {value,label}[] - for select type
 * @props placeholder - string - placeholder text
 * @props min - number - for number inputs
 * @props max - number - for number inputs
 * @props rows - number - for textarea
 * @example
 * <Field id="firstName" label="First name" type="text" value={form.firstName} onChange={...} required />
 * <Field id="contractType" label="Contract type" type="select" value={form.contractType} onChange={...} options={[...]} />
 */

import { useRef } from 'react'
import type { ChangeEvent, ReactElement } from 'react'
import { toDisplayDateOrEmpty, toIsoDateOrEmpty } from '../../lib/date'

interface SelectOption {
  value: string
  label: string
}

interface FieldProps {
  id: string
  label: string
  type?: string
  value?: string | number | boolean
  onChange?: (value: string | boolean) => void
  required?: boolean
  disabled?: boolean
  readOnly?: boolean
  error?: string | null
  options?: SelectOption[]
  placeholder?: string
  min?: number
  max?: number
  rows?: number
}

const baseInput =
  'w-full px-3 py-2 border rounded-md text-sm sm:text-base ' +
  'bg-white text-gray-900 ' +
  'border-gray-300 ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ' +
  'disabled:bg-gray-100 disabled:cursor-not-allowed ' +
  'read-only:bg-gray-50 read-only:cursor-default'

const errorInput = 'border-red-500 focus-visible:ring-red-500'


function CalendarIcon(): ReactElement {
  return (
    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="4" width="18" height="18" rx="2" />
      <line x1="16" y1="2" x2="16" y2="6" />
      <line x1="8" y1="2" x2="8" y2="6" />
      <line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  )
}

/**
 * Floating-label form field that handles all input types used in the app.
 *
 * The label is always explicit (`<label htmlFor={id}>`) not a wrapping label,
 * so assistive technology always associates the label correctly.
 */
export function Field({
  id,
  label,
  type = 'text',
  value,
  onChange,
  required = false,
  disabled = false,
  readOnly = false,
  error,
  options,
  placeholder,
  min,
  max,
  rows = 3,
}: FieldProps): ReactElement {
  const hasError = error !== undefined && error !== null && error !== ''
  // Hidden native date input that only supplies the calendar popup for type="date"
  const pickerRef = useRef<HTMLInputElement>(null)

  const labelEl = (
    <label
      htmlFor={id}
      className="block text-sm font-medium text-gray-700 mb-1"
    >
      {label}
      {required && <span aria-hidden="true" className="ml-1 text-red-500">*</span>}
    </label>
  )

  const errEl = hasError ? (
    <p id={`${id}-error`} role="alert" className="mt-1 text-xs text-red-600">
      {error}
    </p>
  ) : null

  const inputClass = [baseInput, hasError ? errorInput : ''].join(' ')
  const ariaProps = hasError ? { 'aria-describedby': `${id}-error`, 'aria-invalid': true as const } : {}

  if (type === 'checkbox') {
    return (
      <div className="flex items-start gap-2 min-h-[44px]">
        <input
          id={id}
          type="checkbox"
          checked={value === true}
          onChange={(e: ChangeEvent<HTMLInputElement>) => onChange?.(e.target.checked)}
          disabled={disabled || readOnly}
          required={required}
          className="mt-1 h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
          {...ariaProps}
        />
        <div>
          <label htmlFor={id} className="text-sm text-gray-700">
            {label}
            {required && <span aria-hidden="true" className="ml-1 text-red-500">*</span>}
          </label>
          {errEl}
        </div>
      </div>
    )
  }

  if (type === 'select') {
    return (
      <div>
        {labelEl}
        <select
          id={id}
          value={String(value ?? '')}
          onChange={(e: ChangeEvent<HTMLSelectElement>) => onChange?.(e.target.value)}
          disabled={disabled || readOnly}
          required={required}
          className={`${inputClass} min-h-[44px]`}
          {...ariaProps}
        >
          <option value="">— select —</option>
          {options?.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        {errEl}
      </div>
    )
  }

  if (type === 'textarea') {
    return (
      <div>
        {labelEl}
        <textarea
          id={id}
          value={String(value ?? '')}
          onChange={(e: ChangeEvent<HTMLTextAreaElement>) => onChange?.(e.target.value)}
          disabled={disabled}
          readOnly={readOnly}
          required={required}
          rows={rows}
          placeholder={placeholder}
          className={inputClass}
          {...ariaProps}
        />
        {errEl}
      </div>
    )
  }

  // type="date": typed dd-mm-yyyy text box (value format unchanged for every caller)
  // plus a calendar button that opens the browser's date picker and writes back dd-mm-yyyy.
  if (type === 'date') {
    const openPicker = (): void => {
      const picker = pickerRef.current
      if (picker === null) return
      if (typeof picker.showPicker === 'function') picker.showPicker()
      else picker.click()
    }
    return (
      <div>
        {labelEl}
        <div className="relative">
          <input
            id={id}
            type="text"
            inputMode="numeric"
            value={String(value ?? '')}
            onChange={(e: ChangeEvent<HTMLInputElement>) => onChange?.(e.target.value)}
            disabled={disabled}
            readOnly={readOnly}
            required={required}
            placeholder={placeholder ?? 'dd-mm-yyyy'}
            className={`${inputClass} min-h-[44px] pr-11`}
            {...ariaProps}
          />
          {!disabled && !readOnly && (
            <button
              type="button"
              onClick={openPicker}
              aria-label={`Pick ${label} from calendar`}
              className="absolute inset-y-0 right-0 flex items-center justify-center w-11 text-gray-500 hover:text-blue-600 rounded-r-md focus-visible:ring-2 focus-visible:ring-blue-500"
            >
              <CalendarIcon />
            </button>
          )}
          <input
            ref={pickerRef}
            type="date"
            tabIndex={-1}
            aria-hidden="true"
            value={toIsoDateOrEmpty(String(value ?? ''))}
            min={min !== undefined ? String(min) : undefined}
            max={max !== undefined ? String(max) : undefined}
            onChange={(e: ChangeEvent<HTMLInputElement>) => onChange?.(toDisplayDateOrEmpty(e.target.value))}
            // Spans the field's width at its bottom edge: the browser anchors the calendar
            // popup to this element, so it opens directly under the field
            className="absolute left-0 bottom-0 w-full h-0 p-0 border-0 opacity-0 pointer-events-none"
          />
        </div>
        {errEl}
      </div>
    )
  }

  return (
    <div>
      {labelEl}
      <input
        id={id}
        type={type}
        value={type === 'number' ? String(value ?? '') : String(value ?? '')}
        onChange={(e: ChangeEvent<HTMLInputElement>) => onChange?.(e.target.value)}
        disabled={disabled}
        readOnly={readOnly}
        required={required}
        placeholder={placeholder}
        min={min}
        max={max}
        className={`${inputClass} min-h-[44px]`}
        {...ariaProps}
      />
      {errEl}
    </div>
  )
}
