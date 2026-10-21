/**
 * @component ProfileTab
 * @description Employee record — Profile tab (S11, A32/A33/A34).
 * HR can edit: all profile fields, application role, login email.
 * Manager sees this as read-only.
 * Includes "(Re)send set-password e-mail" button for HR (spec §11).
 * Uses optimistic locking (R-DB-5, R-API-3: 409 STALE_VERSION).
 * @props employeeId - number - the employee to display/edit
 * @props canEdit - boolean - true for HR only; false means read-only
 * @example
 * <ProfileTab employeeId={42} canEdit={true} />
 */

import { useEffect, useState } from 'react'
import type { ReactElement } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getEmployeeProfile,
  updateEmployeeProfile,
  resendInvite,
} from '../../../api/employees'
import type { EmployeeProfileDto } from '../../../api/employees'
import { ApiResponseError } from '../../../api/types'
import type { ApiValidationError } from '../../../api/types'
import { Card, Field, useToast } from '../../../components/index'
import { formatDate } from '../../../lib/date'

interface ProfileTabProps {
  employeeId: number
  canEdit: boolean
}

interface ProfileForm {
  firstName: string
  lastName: string
  privateEmail: string
  mobilePhone: string
  street: string
  postcode: string
  city: string
  emergencyName: string
  emergencyRelationship: string
  emergencyPhone: string
  appRole: string
  loginEmail: string
}

type FormErrors = Partial<Record<keyof ProfileForm | '_general', string>>

const APP_ROLE_OPTIONS = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'HR', label: 'HR' },
  { value: 'IT_ADMIN', label: 'IT admin' },
]

function profileToForm(p: EmployeeProfileDto): ProfileForm {
  return {
    firstName: p.firstName,
    lastName: p.lastName,
    privateEmail: p.privateEmail,
    mobilePhone: p.mobilePhone,
    street: p.street,
    postcode: p.postcode,
    city: p.city,
    emergencyName: p.emergencyName ?? '',
    emergencyRelationship: p.emergencyRelationship ?? '',
    emergencyPhone: p.emergencyPhone ?? '',
    appRole: p.appRole ?? 'EMPLOYEE',
    loginEmail: p.loginEmail ?? '',
  }
}

/**
 * ProfileTab shows a full editable profile for HR or a read-only view for managers.
 *
 * The "(Re)send set-password e-mail" button (A34) is shown until account_status = ACTIVE,
 * per spec §11. Version-based optimistic locking is enforced: a 409 STALE_VERSION prompts
 * the user to reload.
 */
export function ProfileTab({ employeeId, canEdit }: ProfileTabProps): ReactElement {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [form, setForm] = useState<ProfileForm | null>(null)
  const [errors, setErrors] = useState<FormErrors>({})

  const { data: profile, isLoading } = useQuery({
    queryKey: ['employee-profile', employeeId],
    queryFn: () => getEmployeeProfile(employeeId),
  })

  // Seed form when profile loads
  useEffect(() => {
    if (profile !== undefined) {
      setForm(profileToForm(profile))
    }
  }, [profile])

  const saveMutation = useMutation({
    mutationFn: (payload: Partial<ProfileForm> & { version?: number | null }) =>
      updateEmployeeProfile(employeeId, payload),
    onSuccess: () => {
      showToast('Profile saved.')
      void queryClient.invalidateQueries({ queryKey: ['employee-profile', employeeId] })
      void queryClient.invalidateQueries({ queryKey: ['employee-overview', employeeId] })
      void queryClient.invalidateQueries({ queryKey: ['employees'] })
      // Back to the list once the edit is saved (only HR can edit, and HR owns /employees)
      void navigate('/employees')
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED') {
        const ve = err.body as ApiValidationError
        const fieldErrs: FormErrors = {}
        ve.errors.forEach((e) => {
          if (e.field !== undefined && e.field !== '') {
            fieldErrs[e.field as keyof ProfileForm] = e.message
          } else {
            fieldErrs._general = e.message
          }
        })
        setErrors(fieldErrs)
      } else if (err instanceof ApiResponseError && err.body.code === 'STALE_VERSION') {
        showToast('Data changed by someone else. Please reload.', 'error')
      } else {
        showToast('Failed to save profile.', 'error')
      }
    },
  })

  const resendMutation = useMutation({
    mutationFn: () => resendInvite(employeeId),
    onSuccess: () => showToast('Set-password e-mail sent.'),
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'ALREADY_LOGGED_IN') {
        showToast('Employee has already logged in; no invite needed.', 'error')
      } else {
        showToast('Failed to send invite.', 'error')
      }
    },
  })

  const setField = <K extends keyof ProfileForm>(key: K, val: string): void => {
    setForm((prev) => (prev !== null ? { ...prev, [key]: val } : null))
    setErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  const handleSave = (): void => {
    if (form === null || profile === undefined) return
    saveMutation.mutate({ ...form, version: profile.version })
  }

  const handleCancel = (): void => {
    if (profile !== undefined) {
      setForm(profileToForm(profile))
      setErrors({})
    }
  }

  if (isLoading || form === null || profile === undefined) {
    return (
      <div className="animate-pulse space-y-4">
        <div className="h-6 bg-gray-200 rounded w-40" />
        <div className="h-40 bg-gray-200 rounded" />
      </div>
    )
  }

  const showResend = profile.accountStatus !== 'ACTIVE'

  return (
    <div className="space-y-6">
      {/* Personal */}
      <Card title="Personal details">
        {errors._general !== undefined && (
          <p role="alert" className="text-xs text-red-600 mb-3">{errors._general}</p>
        )}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field
            id="prof-firstName"
            label="First name"
            value={form.firstName}
            onChange={(v) => setField('firstName', String(v))}
            required
            readOnly={!canEdit}
            error={errors.firstName}
          />
          <Field
            id="prof-lastName"
            label="Last name"
            value={form.lastName}
            onChange={(v) => setField('lastName', String(v))}
            required
            readOnly={!canEdit}
            error={errors.lastName}
          />
          <Field
            id="prof-privateEmail"
            label="Private e-mail"
            type="email"
            value={form.privateEmail}
            onChange={(v) => setField('privateEmail', String(v))}
            required
            readOnly={!canEdit}
            error={errors.privateEmail}
          />
          <Field
            id="prof-mobilePhone"
            label="Mobile phone"
            type="tel"
            value={form.mobilePhone}
            onChange={(v) => setField('mobilePhone', String(v))}
            required
            readOnly={!canEdit}
            error={errors.mobilePhone}
          />
          {profile.dateOfBirth !== null && (
            <div>
              <p className="text-xs text-gray-500 mb-1">Date of birth</p>
              <p className="text-sm text-gray-900">{formatDate(profile.dateOfBirth)}</p>
            </div>
          )}
          {profile.workEmail !== null && (
            <div>
              <p className="text-xs text-gray-500 mb-1">Work e-mail</p>
              <p className="text-sm text-gray-900">{profile.workEmail}</p>
            </div>
          )}
          <div className="sm:col-span-2">
            <Field
              id="prof-street"
              label="Street + number"
              value={form.street}
              onChange={(v) => setField('street', String(v))}
              required
              readOnly={!canEdit}
              error={errors.street}
            />
          </div>
          <Field
            id="prof-postcode"
            label="Postcode"
            value={form.postcode}
            onChange={(v) => setField('postcode', String(v))}
            required
            readOnly={!canEdit}
            error={errors.postcode}
          />
          <Field
            id="prof-city"
            label="City"
            value={form.city}
            onChange={(v) => setField('city', String(v))}
            required
            readOnly={!canEdit}
            error={errors.city}
          />
        </div>
      </Card>

      {/* Emergency contact */}
      <Card title="Emergency contact">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field
            id="prof-emergencyName"
            label="Name"
            value={form.emergencyName}
            onChange={(v) => setField('emergencyName', String(v))}
            readOnly={!canEdit}
            error={errors.emergencyName}
          />
          <Field
            id="prof-emergencyRelationship"
            label="Relationship (optional)"
            value={form.emergencyRelationship}
            onChange={(v) => setField('emergencyRelationship', String(v))}
            readOnly={!canEdit}
          />
          <Field
            id="prof-emergencyPhone"
            label="Phone"
            type="tel"
            value={form.emergencyPhone}
            onChange={(v) => setField('emergencyPhone', String(v))}
            readOnly={!canEdit}
            error={errors.emergencyPhone}
          />
        </div>
      </Card>

      {/* Role & access (HR only — spec §11) */}
      {canEdit && (
        <Card title="Role & access">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field
              id="prof-appRole"
              label="Application role"
              type="select"
              value={form.appRole}
              onChange={(v) => setField('appRole', String(v))}
              options={APP_ROLE_OPTIONS}
              error={errors.appRole}
            />
            <Field
              id="prof-loginEmail"
              label="Login e-mail"
              type="email"
              value={form.loginEmail}
              onChange={(v) => setField('loginEmail', String(v))}
              error={errors.loginEmail}
            />
            {/* (Re)send set-password e-mail — shown until account is ACTIVE (spec §11) */}
            {showResend && (
              <div className="sm:col-span-2 flex">
                <button
                  type="button"
                  onClick={() => resendMutation.mutate()}
                  disabled={resendMutation.isPending}
                  className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
                >
                  {resendMutation.isPending ? 'Sending…' : '(Re)send set-password e-mail'}
                </button>
              </div>
            )}
          </div>
        </Card>
      )}

      {/* Action bar — only visible when editing is allowed (R-UI-1) */}
      {canEdit && (
        <div className="flex justify-between items-center pt-4 border-t border-gray-200">
          <button
            type="button"
            onClick={handleCancel}
            className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-500 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saveMutation.isPending}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
          >
            {saveMutation.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      )}
    </div>
  )
}
