/**
 * Field type="date": typed dd-mm-yyyy text box plus a calendar button whose picker
 * writes back dd-mm-yyyy, so callers keep their existing display-format state.
 */

import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { Field } from './Field'

describe('Field — date', () => {
  it('shows a calendar button next to the dd-mm-yyyy text box', () => {
    render(<Field id="startDate" label="Start date" type="date" value="05-11-2026" onChange={vi.fn()} />)
    expect((screen.getByLabelText('Start date') as HTMLInputElement).value).toBe('05-11-2026')
    expect(screen.getByRole('button', { name: 'Pick Start date from calendar' })).toBeTruthy()
  })

  it('passes a date picked in the calendar back as dd-mm-yyyy', () => {
    const onChange = vi.fn()
    const { container } = render(<Field id="startDate" label="Start date" type="date" value="" onChange={onChange} />)
    const picker = container.querySelector('input[type="date"]') as HTMLInputElement
    fireEvent.change(picker, { target: { value: '2026-11-05' } })
    expect(onChange).toHaveBeenCalledWith('05-11-2026')
  })

  it('hides the calendar button when read-only', () => {
    render(<Field id="startDate" label="Start date" type="date" value="05-11-2026" readOnly />)
    expect(screen.queryByRole('button')).toBeNull()
  })
})
