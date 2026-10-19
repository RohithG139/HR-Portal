/**
 * Shared wireframe building blocks for the Settings tabs (WF-08 `.card`, `.card-title-row`,
 * `.btn-xs`, `th.sortable`, `.btn-x`), so every tab looks the same.
 */

import type { ReactElement } from 'react'

/** White card with border and soft shadow (wireframe `.card`). */
export const CARD = 'bg-white border border-gray-200 rounded-xl px-6 py-[22px] shadow-[0_1px_3px_rgba(15,23,42,0.06),0_1px_2px_rgba(15,23,42,0.04)] min-w-0'

/** Card header: title left, actions right, divider below (wireframe `.card-title-row`). */
export const CARD_TITLE_ROW = 'flex flex-wrap items-center justify-between gap-3 pb-3 mb-[18px] border-b border-gray-200'

/** Card title text (wireframe `.card-title`). */
export const CARD_TITLE = 'font-display text-[17px] font-bold text-gray-900'

/** Grey toolbar above a table holding filters/search (wireframe `.table-toolbar`). */
export const TABLE_TOOLBAR = 'flex flex-wrap items-center justify-end gap-3 px-4 py-3.5 bg-gray-50 border-b border-gray-200'

/** Search input (wireframe `.search-in`). */
export const SEARCH_INPUT = 'w-60 max-w-full px-3 py-2 text-[13px] border-[1.5px] border-gray-300 rounded-md bg-white text-gray-900 placeholder:text-gray-400 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500'

/** Compact select / number input used inside table rows (wireframe `.field-select` / `.inline-num`). */
export const INLINE_FIELD = 'px-3 py-1.5 text-[13px] border-[1.5px] border-gray-300 rounded-md bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500'

/** INLINE_FIELD for a <select>, with the wireframe's grey triangle instead of the native arrow. */
export const INLINE_SELECT = `${INLINE_FIELD} appearance-none pr-8 bg-no-repeat bg-[position:right_10px_center] bg-[url("data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='10'%20height='7'%20viewBox='0%200%2012%208'%3E%3Cpath%20fill='%236b7280'%20d='M6%208L0%200h12z'/%3E%3C/svg%3E")]`

/** Status pill with leading dot (wireframe `.badge` / `.badge-success`). */
export function StatusBadge({ tone, children }: { tone: 'success' | 'neutral' | 'warning'; children: string }): ReactElement {
  const colours = {
    success: 'bg-green-100 text-green-800',
    neutral: 'bg-gray-100 text-gray-600',
    warning: 'bg-amber-100 text-amber-800',
  }[tone]
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-[3px] rounded-full text-[11px] font-semibold uppercase tracking-[0.2px] whitespace-nowrap ${colours}`}>
      <span aria-hidden="true" className="w-1.5 h-1.5 rounded-full bg-current" />
      {children}
    </span>
  )
}

interface SwitchProps {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  disabled?: boolean
}

/** On/off switch (wireframe `.switch`: 38×22 track, 16px knob, green when on). */
export function Switch({ checked, onChange, label, disabled = false }: SwitchProps): ReactElement {
  return (
    <label className={`inline-flex items-center min-h-[44px] ${disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}>
      <span className="relative inline-block w-[38px] h-[22px] shrink-0">
        <input
          type="checkbox"
          role="switch"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onChange(e.target.checked)}
          aria-label={label}
          className="sr-only peer"
        />
        <span aria-hidden="true" className="absolute inset-0 rounded-full bg-gray-300 transition-colors peer-checked:bg-green-600 peer-focus-visible:ring-2 peer-focus-visible:ring-blue-500" />
        <span aria-hidden="true" className="absolute top-[3px] left-[3px] w-4 h-4 rounded-full bg-white shadow-sm transition-transform peer-checked:translate-x-4" />
      </span>
    </label>
  )
}

const BTN_XS = 'inline-flex items-center h-8 px-3 text-[11px] font-semibold uppercase tracking-[0.4px] rounded-md transition-colors'

/** Small green positive action, e.g. "+ Add item" (wireframe `.btn-primary.btn-xs`). */
export const ADD_BTN = `${BTN_XS} bg-green-600 hover:bg-green-700 text-white shadow-sm focus-visible:ring-2 focus-visible:ring-green-500`

/** Small outlined secondary action, e.g. "+ New template", "Edit" (wireframe `.btn-secondary.btn-xs`). */
export const SECONDARY_BTN = `${BTN_XS} border-[1.5px] border-gray-300 bg-white text-gray-600 hover:bg-gray-50 hover:text-gray-900 hover:border-gray-500 focus-visible:ring-2 focus-visible:ring-blue-500`

/** Round "×" remove button (wireframe `.btn-x`). */
export const REMOVE_BTN = 'inline-flex items-center justify-center w-[30px] h-[30px] rounded-full border border-gray-300 bg-white text-gray-500 hover:border-red-600 hover:text-red-600 hover:bg-red-50 focus-visible:ring-2 focus-visible:ring-red-500 transition-colors'

/** Cross for the round remove button — an SVG so it sits exactly centred (a text "×" doesn't). */
export function CloseIcon(): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
      <path d="M2.5 2.5l7 7M9.5 2.5l-7 7" />
    </svg>
  )
}

export interface SortState {
  key: string
  dir: 'asc' | 'desc'
}

/** Initial state: no column sorted, rows keep the API's order (wireframe shows ⇅ on every column). */
export const UNSORTED: SortState = { key: '', dir: 'asc' }

/** Returns a sorted copy of rows by the active sort key (R-UI-2: every table sortable). */
export function sortRows<T>(rows: T[], sort: SortState, value: (row: T, key: string) => string | number): T[] {
  if (sort.key === '') return rows
  const factor = sort.dir === 'asc' ? 1 : -1
  return [...rows].sort((a, b) => {
    const va = value(a, sort.key)
    const vb = value(b, sort.key)
    if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * factor
    return String(va).localeCompare(String(vb)) * factor
  })
}

interface SortHeaderProps {
  label: string
  sortKey: string
  sort: SortState
  onSort: (s: SortState) => void
  center?: boolean
}

/** Clickable column header with the wireframe's sort indicator. */
export function SortHeader({ label, sortKey, sort, onSort, center = false }: SortHeaderProps): ReactElement {
  const active = sort.key === sortKey
  return (
    <th
      scope="col"
      className={center ? 'text-center' : undefined}
      aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
    >
      <button
        type="button"
        onClick={() => onSort({ key: sortKey, dir: active && sort.dir === 'asc' ? 'desc' : 'asc' })}
        className="inline-flex items-center gap-1.5 uppercase font-semibold focus-visible:ring-2 focus-visible:ring-white rounded"
      >
        {label}
        <span aria-hidden="true" className={active ? '' : 'opacity-55 text-[11px]'}>
          {active ? (sort.dir === 'asc' ? '↑' : '↓') : '⇅'}
        </span>
      </button>
    </th>
  )
}

/** Underlined tab button, same look as the Settings page tabs (wireframe `.tab` / `.tab.on`). */
export function tabClass(active: boolean): string {
  return [
    'px-[22px] py-[11px] -mb-[2px] text-[13px] font-semibold tracking-[0.2px] border-b-[3px] min-h-[44px]',
    'focus-visible:ring-2 focus-visible:ring-blue-500 transition-colors',
    active
      ? 'border-blue-600 text-blue-600 bg-gradient-to-b from-blue-50 to-transparent'
      : 'border-transparent text-gray-500 hover:text-blue-600',
  ].join(' ')
}
