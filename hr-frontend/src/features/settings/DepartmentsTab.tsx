/**
 * @component DepartmentsTab
 * @description S1 — Settings tab 1: Departments & Job titles.
 * Two side-by-side lists, each with "+ Add" modal and "×" to remove rows.
 * Handles ADM-03 (in-use removal blocked with toast) and DUPLICATE_NAME (field error).
 * @props (none)
 * @example
 * <DepartmentsTab />
 */

// "use client" not needed — Vite React, all components are client-side.

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  listDepartments,
  createDepartment,
  deleteDepartment,
  listJobTitles,
  createJobTitle,
  deleteJobTitle,
} from '../../api/settingsDepartments'
import type { DepartmentDto, JobTitleDto } from '../../api/settingsDepartments'
import { Modal, Field, useToast } from '../../components/index'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Map a 400 VALIDATION_FAILED body to the first matching field's message. */
function getFieldError(body: ApiValidationError, field: string): string | null {
  const match = body.errors.find((e) => e.field === field)
  return match?.message ?? null
}

// ---------------------------------------------------------------------------
// Add Department modal
// ---------------------------------------------------------------------------

interface AddDeptModalProps {
  isOpen: boolean
  onClose: () => void
  onAdded: () => void
}

function AddDepartmentModal({ isOpen, onClose, onAdded }: AddDeptModalProps): ReactElement {
  const { showToast } = useToast()
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const reset = (): void => {
    setName('')
    setNameError(null)
  }

  const handleClose = (): void => {
    reset()
    onClose()
  }

  const handleSubmit = async (): Promise<void> => {
    setNameError(null)
    if (name.trim() === '') {
      setNameError('This field is required.')
      return
    }
    setSaving(true)
    try {
      await createDepartment(name.trim())
      showToast('Department added.', 'success')
      reset()
      onAdded()
      onClose()
    } catch (err: unknown) {
      if (err instanceof ApiResponseError) {
        if (err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
          setNameError(getFieldError(err.body as ApiValidationError, 'name'))
        } else if (err.body.code === 'DUPLICATE_NAME') {
          setNameError('This name already exists.')
        } else {
          showToast('Failed to add department.', 'error')
        }
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      title="Add department"
      onClose={handleClose}
      footer={
        <>
          {/* Cancel — red outline, left (R-UI-1) */}
          <button
            type="button"
            onClick={handleClose}
            className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-600 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500"
          >
            Cancel
          </button>
          {/* Positive action — green, right (R-UI-1) */}
          <button
            type="button"
            onClick={() => { void handleSubmit() }}
            disabled={saving}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
          >
            {saving ? 'Adding…' : 'Add department'}
          </button>
        </>
      }
    >
      <Field
        id="dept-name"
        label="Department name"
        value={name}
        onChange={(v) => setName(String(v))}
        required
        error={nameError}
        placeholder="e.g. Engineering"
      />
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Add Job title modal
// ---------------------------------------------------------------------------

interface AddJobTitleModalProps {
  isOpen: boolean
  onClose: () => void
  onAdded: () => void
}

function AddJobTitleModal({ isOpen, onClose, onAdded }: AddJobTitleModalProps): ReactElement {
  const { showToast } = useToast()
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const reset = (): void => {
    setName('')
    setNameError(null)
  }

  const handleClose = (): void => {
    reset()
    onClose()
  }

  const handleSubmit = async (): Promise<void> => {
    setNameError(null)
    if (name.trim() === '') {
      setNameError('This field is required.')
      return
    }
    setSaving(true)
    try {
      await createJobTitle(name.trim())
      showToast('Job title added.', 'success')
      reset()
      onAdded()
      onClose()
    } catch (err: unknown) {
      if (err instanceof ApiResponseError) {
        if (err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
          setNameError(getFieldError(err.body as ApiValidationError, 'name'))
        } else if (err.body.code === 'DUPLICATE_NAME') {
          setNameError('This name already exists.')
        } else {
          showToast('Failed to add job title.', 'error')
        }
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      title="Add job title"
      onClose={handleClose}
      footer={
        <>
          <button
            type="button"
            onClick={handleClose}
            className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-600 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => { void handleSubmit() }}
            disabled={saving}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
          >
            {saving ? 'Adding…' : 'Add job title'}
          </button>
        </>
      }
    >
      <Field
        id="job-title-name"
        label="Job title"
        value={name}
        onChange={(v) => setName(String(v))}
        required
        error={nameError}
        placeholder="e.g. Senior Developer"
      />
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

/**
 * DepartmentsTab renders the S1 settings tab (Departments & Job titles).
 * Uses React Query for data fetching; invalidates on mutation so lists stay fresh.
 */
export function DepartmentsTab(): ReactElement {
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const [addDeptOpen, setAddDeptOpen] = useState(false)
  const [addJobOpen, setAddJobOpen] = useState(false)

  const { data: departments, isLoading: deptsLoading } = useQuery({
    queryKey: ['settings', 'departments'],
    queryFn: listDepartments,
  })

  const { data: jobTitles, isLoading: jobsLoading } = useQuery({
    queryKey: ['settings', 'job-titles'],
    queryFn: listJobTitles,
  })

  const handleDeleteDept = async (dept: DepartmentDto): Promise<void> => {
    try {
      await deleteDepartment(dept.id)
      showToast(`"${dept.name}" removed.`, 'success')
      void queryClient.invalidateQueries({ queryKey: ['settings', 'departments'] })
    } catch (err: unknown) {
      if (err instanceof ApiResponseError && err.body.code === 'ITEM_IN_USE') {
        showToast('This item is in use and cannot be removed.', 'error')
      } else {
        showToast('Failed to remove department.', 'error')
      }
    }
  }

  const handleDeleteJobTitle = async (jt: JobTitleDto): Promise<void> => {
    try {
      await deleteJobTitle(jt.id)
      showToast(`"${jt.name}" removed.`, 'success')
      void queryClient.invalidateQueries({ queryKey: ['settings', 'job-titles'] })
    } catch (err: unknown) {
      if (err instanceof ApiResponseError && err.body.code === 'ITEM_IN_USE') {
        showToast('This item is in use and cannot be removed.', 'error')
      } else {
        showToast('Failed to remove job title.', 'error')
      }
    }
  }

  const invalidateDepts = (): void => {
    void queryClient.invalidateQueries({ queryKey: ['settings', 'departments'] })
  }

  const invalidateJobs = (): void => {
    void queryClient.invalidateQueries({ queryKey: ['settings', 'job-titles'] })
  }

  return (
    <div className="space-y-6">
      {/* Two-column layout: stacked on mobile, side-by-side on md+ */}
      <div className="flex flex-col md:flex-row gap-6">

        {/* Departments list */}
        <section aria-labelledby="dept-heading" className="flex-1 min-w-0">
          <div className="flex items-center justify-between mb-3">
            <h2
              id="dept-heading"
              className="text-base font-semibold text-gray-900"
            >
              Departments
            </h2>
            {/* + Add department — top right of its section (R-UI-1) */}
            <button
              type="button"
              onClick={() => setAddDeptOpen(true)}
              className="px-3 py-1.5 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500"
            >
              + Add department
            </button>
          </div>

          <div className="overflow-x-auto rounded-lg border border-gray-200">
            <table className="w-full text-sm text-left text-gray-700">
              <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                <tr>
                  <th scope="col" className="px-4 py-3">Department</th>
                  <th scope="col" className="px-4 py-3">People</th>
                  <th scope="col" className="px-4 py-3 w-12">Remove</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {deptsLoading && (
                  <tr>
                    <td colSpan={3} className="px-4 py-6 text-center text-gray-400">
                      Loading…
                    </td>
                  </tr>
                )}
                {!deptsLoading && (departments ?? []).length === 0 && (
                  <tr>
                    <td colSpan={3} className="px-4 py-6 text-center text-gray-400">
                      No departments yet.
                    </td>
                  </tr>
                )}
                {(departments ?? []).map((dept) => (
                  <tr
                    key={dept.id}
                    className="bg-white hover:bg-gray-50"
                  >
                    <td className="px-4 py-3 font-medium">{dept.name}</td>
                    <td className="px-4 py-3 text-gray-500">{dept.employeeCount}</td>
                    <td className="px-4 py-3">
                      {/* Small × to remove list items (R-UI-1) */}
                      <button
                        type="button"
                        onClick={() => { void handleDeleteDept(dept) }}
                        aria-label={`Remove ${dept.name}`}
                        className="flex items-center justify-center min-h-[44px] min-w-[44px] text-gray-400 hover:text-red-600 focus-visible:ring-2 focus-visible:ring-red-500 rounded"
                      >
                        <span aria-hidden="true">×</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        {/* Job titles list */}
        <section aria-labelledby="job-heading" className="flex-1 min-w-0">
          <div className="flex items-center justify-between mb-3">
            <h2
              id="job-heading"
              className="text-base font-semibold text-gray-900"
            >
              Job titles
            </h2>
            {/* + Add job title — top right of its section (R-UI-1) */}
            <button
              type="button"
              onClick={() => setAddJobOpen(true)}
              className="px-3 py-1.5 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500"
            >
              + Add job title
            </button>
          </div>

          <div className="overflow-x-auto rounded-lg border border-gray-200">
            <table className="w-full text-sm text-left text-gray-700">
              <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                <tr>
                  <th scope="col" className="px-4 py-3">Job title</th>
                  <th scope="col" className="px-4 py-3 w-12">Remove</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {jobsLoading && (
                  <tr>
                    <td colSpan={2} className="px-4 py-6 text-center text-gray-400">
                      Loading…
                    </td>
                  </tr>
                )}
                {!jobsLoading && (jobTitles ?? []).length === 0 && (
                  <tr>
                    <td colSpan={2} className="px-4 py-6 text-center text-gray-400">
                      No job titles yet.
                    </td>
                  </tr>
                )}
                {(jobTitles ?? []).map((jt) => (
                  <tr
                    key={jt.id}
                    className="bg-white hover:bg-gray-50"
                  >
                    <td className="px-4 py-3 font-medium">{jt.name}</td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        onClick={() => { void handleDeleteJobTitle(jt) }}
                        aria-label={`Remove ${jt.name}`}
                        className="flex items-center justify-center min-h-[44px] min-w-[44px] text-gray-400 hover:text-red-600 focus-visible:ring-2 focus-visible:ring-red-500 rounded"
                      >
                        <span aria-hidden="true">×</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {/* Modals */}
      <AddDepartmentModal
        isOpen={addDeptOpen}
        onClose={() => setAddDeptOpen(false)}
        onAdded={invalidateDepts}
      />
      <AddJobTitleModal
        isOpen={addJobOpen}
        onClose={() => setAddJobOpen(false)}
        onAdded={invalidateJobs}
      />
    </div>
  )
}
