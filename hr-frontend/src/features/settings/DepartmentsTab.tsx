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
import { getOnboardingLookups } from '../../api/lookups'
import { CARD, CARD_TITLE_ROW, CARD_TITLE, ADD_BTN, REMOVE_BTN, CloseIcon, SortHeader, sortRows, UNSORTED } from './settingsUi'
import type { SortState } from './settingsUi'
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
// Helpers
// ---------------------------------------------------------------------------

/** SENIOR_II → "Senior II", MEDIOR → "Medior" (wireframe display). */
export function gradeLabel(grade: string): string {
  return grade
    .split('_')
    .map((part, i) => (i === 0 ? part.charAt(0) + part.slice(1).toLowerCase() : part))
    .join(' ')
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
  grades: string[]
  departments: DepartmentDto[]
}

function AddJobTitleModal({ isOpen, onClose, onAdded, grades, departments }: AddJobTitleModalProps): ReactElement {
  const { showToast } = useToast()
  const [name, setName] = useState('')
  const [grade, setGrade] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [gradeError, setGradeError] = useState<string | null>(null)
  const [deptError, setDeptError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const reset = (): void => {
    setName('')
    setGrade('')
    setDepartmentId('')
    setNameError(null)
    setGradeError(null)
    setDeptError(null)
  }

  const handleClose = (): void => {
    reset()
    onClose()
  }

  const handleSubmit = async (): Promise<void> => {
    const required = 'This field is required.'
    const nErr = name.trim() === '' ? required : null
    const gErr = grade === '' ? required : null
    const dErr = departmentId === '' ? required : null
    setNameError(nErr)
    setGradeError(gErr)
    setDeptError(dErr)
    if (nErr !== null || gErr !== null || dErr !== null) return
    setSaving(true)
    try {
      await createJobTitle(name.trim(), grade, Number(departmentId))
      showToast('Job title added.', 'success')
      reset()
      onAdded()
      onClose()
    } catch (err: unknown) {
      if (err instanceof ApiResponseError) {
        if (err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
          const body = err.body as ApiValidationError
          setNameError(getFieldError(body, 'name'))
          setGradeError(getFieldError(body, 'grade'))
          setDeptError(getFieldError(body, 'departmentId'))
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
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
        <Field
          id="job-title-grade"
          label="Grade"
          type="select"
          value={grade}
          onChange={(v) => setGrade(String(v))}
          required
          error={gradeError}
          options={grades.map((g) => ({ value: g, label: gradeLabel(g) }))}
        />
        <Field
          id="job-title-department"
          label="Department"
          type="select"
          value={departmentId}
          onChange={(v) => setDepartmentId(String(v))}
          required
          error={deptError}
          options={departments.map((d) => ({ value: String(d.id), label: d.name }))}
        />
      </div>
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
  const [deptSort, setDeptSort] = useState<SortState>(UNSORTED)
  const [jobSort, setJobSort] = useState<SortState>(UNSORTED)

  const { data: departments, isLoading: deptsLoading } = useQuery({
    queryKey: ['settings', 'departments'],
    queryFn: listDepartments,
  })

  const { data: jobTitles, isLoading: jobsLoading } = useQuery({
    queryKey: ['settings', 'job-titles'],
    queryFn: listJobTitles,
  })

  // Grade options for the Add job title pop-up (same fixed list as employee.grade)
  const { data: lookups } = useQuery({
    queryKey: ['onboarding-lookups'],
    queryFn: getOnboardingLookups,
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

  const sortedDepts = sortRows(departments ?? [], deptSort, (d, key) =>
    key === 'people' ? d.employeeCount : d.name)
  const sortedJobs = sortRows(jobTitles ?? [], jobSort, (j, key) =>
    key === 'grade'
      ? (lookups?.grades ?? []).indexOf(j.grade ?? '')
      : key === 'department' ? (j.departmentName ?? '') : j.name)

  return (
    <div className="space-y-6">
      {/* Two cards side by side on md+, stacked on mobile (wireframe grid-half) */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-stretch">

        {/* Departments card */}
        <section aria-labelledby="dept-heading" className={CARD}>
          <div className={CARD_TITLE_ROW}>
            <h2 id="dept-heading" className={CARD_TITLE}>
              Departments
            </h2>
            {/* + Add department — top right of its section (R-UI-1) */}
            <button
              type="button"
              onClick={() => setAddDeptOpen(true)}
              className={ADD_BTN}
            >
              + Add department
            </button>
          </div>

          <div className="wf-table-wrap">
            <table className="wf-table">
              <thead>
                <tr>
                  <SortHeader label="Department" sortKey="name" sort={deptSort} onSort={setDeptSort} />
                  <SortHeader label="People" sortKey="people" sort={deptSort} onSort={setDeptSort} center />
                  <th scope="col" className="w-24 text-center">Action</th>
                </tr>
              </thead>
              <tbody>
                {deptsLoading && (
                  <tr>
                    <td colSpan={3} className="text-center text-gray-400">
                      Loading…
                    </td>
                  </tr>
                )}
                {!deptsLoading && sortedDepts.length === 0 && (
                  <tr>
                    <td colSpan={3} className="text-center text-gray-400">
                      No departments yet.
                    </td>
                  </tr>
                )}
                {sortedDepts.map((dept) => (
                  <tr key={dept.id}>
                    <td>{dept.name}</td>
                    <td className="text-center tabular-nums">{dept.employeeCount}</td>
                    <td className="text-center">
                      {/* Small × to remove list items (R-UI-1) */}
                      <button
                        type="button"
                        onClick={() => { void handleDeleteDept(dept) }}
                        aria-label={`Remove ${dept.name}`}
                        className={REMOVE_BTN}
                      >
                        <CloseIcon />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        {/* Job titles card */}
        <section aria-labelledby="job-heading" className={CARD}>
          <div className={CARD_TITLE_ROW}>
            <h2 id="job-heading" className={CARD_TITLE}>
              Job titles
            </h2>
            {/* + Add job title — top right of its section (R-UI-1) */}
            <button
              type="button"
              onClick={() => setAddJobOpen(true)}
              className={ADD_BTN}
            >
              + Add job title
            </button>
          </div>

          <div className="wf-table-wrap">
            <table className="wf-table">
              <thead>
                <tr>
                  <SortHeader label="Job title" sortKey="name" sort={jobSort} onSort={setJobSort} />
                  <SortHeader label="Grade" sortKey="grade" sort={jobSort} onSort={setJobSort} />
                  <SortHeader label="Department" sortKey="department" sort={jobSort} onSort={setJobSort} />
                  <th scope="col" className="w-24 text-center">Action</th>
                </tr>
              </thead>
              <tbody>
                {jobsLoading && (
                  <tr>
                    <td colSpan={4} className="text-center text-gray-400">
                      Loading…
                    </td>
                  </tr>
                )}
                {!jobsLoading && sortedJobs.length === 0 && (
                  <tr>
                    <td colSpan={4} className="text-center text-gray-400">
                      No job titles yet.
                    </td>
                  </tr>
                )}
                {sortedJobs.map((jt) => (
                  <tr key={jt.id}>
                    <td>{jt.name}</td>
                    <td>{jt.grade !== null ? gradeLabel(jt.grade) : '—'}</td>
                    <td>{jt.departmentName ?? '—'}</td>
                    <td className="text-center">
                      <button
                        type="button"
                        onClick={() => { void handleDeleteJobTitle(jt) }}
                        aria-label={`Remove ${jt.name}`}
                        className={REMOVE_BTN}
                      >
                        <CloseIcon />
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
        grades={lookups?.grades ?? []}
        departments={departments ?? []}
      />
    </div>
  )
}
