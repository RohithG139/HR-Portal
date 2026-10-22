/**
 * @component MyProfile
 * @description S10 — Employee self-service profile (`p-sb-profile`).
 * Editable: private e-mail, mobile phone, street, postcode, city, emergency contact.
 * Read-only: first name, last name, work e-mail, date of birth, employment card (WS-03).
 * Uses A29 (read) and A30 (write).
 * @props (none — employee reads own profile from /api/me/profile)
 * @example
 * Route: { path: '/my-profile', element: <MyProfile /> }
 */

import { useEffect, useState } from 'react'
import type { ReactElement } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getMyProfile, updateMyProfile } from '../../api/me'
import type { MyProfileUpdateRequest } from '../../api/me'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'
import { Card, Field, useToast } from '../../components/index'
import { formatDate } from '../../lib/date'
import { isValidEmail, isValidPhone } from '../../lib/validation'

interface EditForm {
  privateEmail: string
  mobilePhone: string
  street: string
  postcode: string
  city: string
  emergencyName: string
  emergencyRelationship: string
  emergencyPhone: string
}

type FormErrors = Partial<Record<keyof EditForm | '_general', string>>

const POSTCODE_RE = /^[1-9][0-9]{3}\s?[A-Z]{2}$/i

function validatePostcode(v: string): boolean {
  return POSTCODE_RE.test(v.trim())
}

/**
 * MyProfile renders the employee's editable profile card.
 *
 * Employment details (name, DOB, work email, contract) are rendered read-only.
 * The form uses optimistic local state: the query is seeded into `form` on first
 * load and after a successful save.
 */
export function MyProfile(): ReactElement {
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const [form, setForm] = useState<EditForm>({
    privateEmail: '',
    mobilePhone: '',
    street: '',
    postcode: '',
    city: '',
    emergencyName: '',
    emergencyRelationship: '',
    emergencyPhone: '',
  })
  const [errors, setErrors] = useState<FormErrors>({})

  const { data: profile, isLoading } = useQuery({
    queryKey: ['my-profile'],
    queryFn: getMyProfile,
  })

  // Seed form when profile loads (or changes after save)
  useEffect(() => {
    if (profile !== undefined) {
      setForm({
        privateEmail: profile.privateEmail,
        mobilePhone: profile.mobilePhone,
        street: profile.street,
        postcode: profile.postcode,
        city: profile.city,
        emergencyName: profile.emergencyName ?? '',
        emergencyRelationship: profile.emergencyRelationship ?? '',
        emergencyPhone: profile.emergencyPhone ?? '',
      })
    }
  }, [profile])

  const saveMutation = useMutation({
    mutationFn: (payload: MyProfileUpdateRequest) => updateMyProfile(payload),
    onSuccess: () => {
      showToast('Profile saved.')
      void queryClient.invalidateQueries({ queryKey: ['my-profile'] })
      // Saving a complete profile ticks the onboarding PROFILE item (FRD-02 §4.5)
      void queryClient.invalidateQueries({ queryKey: ['my-onboarding'] })
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED') {
        const ve = err.body as ApiValidationError
        const fieldErrs: FormErrors = {}
        ve.errors.forEach((e) => {
          if (e.field !== undefined && e.field !== '') {
            fieldErrs[e.field as keyof EditForm] = e.message
          } else {
            fieldErrs._general = e.message
          }
        })
        setErrors(fieldErrs)
      } else if (
        err instanceof ApiResponseError &&
        err.body.code === 'DUPLICATE_PRIVATE_EMAIL'
      ) {
        setErrors({ privateEmail: 'An employee with this e-mail address already exists.' })
      } else {
        showToast('Failed to save profile.', 'error')
      }
    },
  })

  const validate = (): boolean => {
    const errs: FormErrors = {}
    if (!isValidEmail(form.privateEmail)) errs.privateEmail = 'Enter a valid e-mail address.'
    if (!isValidPhone(form.mobilePhone)) errs.mobilePhone = 'Enter a valid phone number, e.g. +31 6 12345678.'
    if (form.street.trim() === '') errs.street = 'This field is required.'
    if (!validatePostcode(form.postcode)) errs.postcode = 'Enter a valid postcode, e.g. 3511 AV.'
    if (form.city.trim() === '') errs.city = 'This field is required.'
    // Emergency contact: if either name or phone given, both required
    if (
      (form.emergencyName.trim() !== '' || form.emergencyPhone.trim() !== '') &&
      (form.emergencyName.trim() === '' || form.emergencyPhone.trim() === '')
    ) {
      errs.emergencyName = 'Add an emergency contact name and phone number.'
      errs.emergencyPhone = 'Add an emergency contact name and phone number.'
    }
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const handleSave = (): void => {
    if (!validate()) return
    saveMutation.mutate({
      privateEmail: form.privateEmail,
      mobilePhone: form.mobilePhone,
      street: form.street,
      postcode: form.postcode,
      city: form.city,
      emergencyName: form.emergencyName.trim() !== '' ? form.emergencyName.trim() : null,
      emergencyRelationship:
        form.emergencyRelationship.trim() !== '' ? form.emergencyRelationship.trim() : null,
      emergencyPhone: form.emergencyPhone.trim() !== '' ? form.emergencyPhone.trim() : null,
      version: profile?.version ?? null,
    })
  }

  const setField = <K extends keyof EditForm>(key: K, val: string): void => {
    setForm((prev) => ({ ...prev, [key]: val }))
    setErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  if (isLoading) {
    return (
      <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 animate-pulse space-y-4">
        <div className="h-8 bg-gray-200 rounded w-40" />
        <div className="h-40 bg-gray-200 rounded" />
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        My profile
      </h1>

      {/* Personal details (partially editable) */}
      <Card title="Personal details">
        {errors._general !== undefined && (
          <p role="alert" className="text-xs text-red-600 mb-3">{errors._general}</p>
        )}
        {/* Read-only: name and DOB (WS-03) */}
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm mb-4">
          <div>
            <dt className="text-gray-500">First name</dt>
            <dd className="text-gray-900 font-medium">{profile?.firstName ?? ''}</dd>
          </div>
          <div>
            <dt className="text-gray-500">Last name</dt>
            <dd className="text-gray-900 font-medium">{profile?.lastName ?? ''}</dd>
          </div>
          {profile?.dateOfBirth !== null && profile?.dateOfBirth !== undefined && (
            <div>
              <dt className="text-gray-500">Date of birth</dt>
              <dd className="text-gray-900">{formatDate(profile.dateOfBirth)}</dd>
            </div>
          )}
          {profile?.workEmail !== null && profile?.workEmail !== undefined && (
            <div>
              <dt className="text-gray-500">Work e-mail</dt>
              <dd className="text-gray-900">{profile.workEmail}</dd>
            </div>
          )}
        </dl>

        {/* Editable fields */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field
            id="privateEmail"
            label="Private e-mail"
            type="email"
            value={form.privateEmail}
            onChange={(v) => setField('privateEmail', String(v))}
            required
            error={errors.privateEmail}
          />
          <Field
            id="mobilePhone"
            label="Mobile phone"
            type="tel"
            value={form.mobilePhone}
            onChange={(v) => setField('mobilePhone', String(v))}
            required
            placeholder="+31 6 12345678"
            error={errors.mobilePhone}
          />
          <div className="sm:col-span-2">
            <Field
              id="street"
              label="Street + number"
              value={form.street}
              onChange={(v) => setField('street', String(v))}
              required
              error={errors.street}
            />
          </div>
          <Field
            id="postcode"
            label="Postcode"
            value={form.postcode}
            onChange={(v) => setField('postcode', String(v))}
            required
            placeholder="3511 AV"
            error={errors.postcode}
          />
          <Field
            id="city"
            label="City"
            value={form.city}
            onChange={(v) => setField('city', String(v))}
            required
            error={errors.city}
          />
        </div>
      </Card>

      {/* Emergency contact */}
      <Card title="Emergency contact">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field
            id="emergencyName"
            label="Name"
            value={form.emergencyName}
            onChange={(v) => setField('emergencyName', String(v))}
            error={errors.emergencyName}
          />
          <Field
            id="emergencyRelationship"
            label="Relationship (optional)"
            value={form.emergencyRelationship}
            onChange={(v) => setField('emergencyRelationship', String(v))}
          />
          <Field
            id="emergencyPhone"
            label="Phone"
            type="tel"
            value={form.emergencyPhone}
            onChange={(v) => setField('emergencyPhone', String(v))}
            placeholder="+31 6 12345678"
            error={errors.emergencyPhone}
          />
        </div>
      </Card>

      {/* Employment card (read-only, WS-03) */}
      {profile !== null && profile !== undefined && (
        <Card title="Employment (read-only)">
          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
            {profile.jobTitle !== null && (
              <div><dt className="text-gray-500">Job title</dt><dd className="text-gray-900">{profile.jobTitle}</dd></div>
            )}
            {profile.department !== null && (
              <div><dt className="text-gray-500">Department</dt><dd className="text-gray-900">{profile.department}</dd></div>
            )}
            {profile.grade !== null && (
              <div><dt className="text-gray-500">Grade</dt><dd className="text-gray-900">{profile.grade}</dd></div>
            )}
            {profile.contractType !== null && (
              <div><dt className="text-gray-500">Contract type</dt><dd className="text-gray-900">{profile.contractType}</dd></div>
            )}
            {profile.startDate !== null && (
              <div><dt className="text-gray-500">Start date</dt><dd className="text-gray-900">{formatDate(profile.startDate)}</dd></div>
            )}
            {profile.hoursPerWeek !== null && (
              <div><dt className="text-gray-500">Hours per week</dt><dd className="text-gray-900">{profile.hoursPerWeek}</dd></div>
            )}
          </dl>
        </Card>
      )}

      {/* Action bar (R-UI-1) */}
      <div className="flex justify-between items-center pt-4 border-t border-gray-200">
        <button
          type="button"
          onClick={() => {
            // Cancel resets the form to the last loaded profile data (G-11)
            if (profile !== undefined) {
              setForm({
                privateEmail: profile.privateEmail,
                mobilePhone: profile.mobilePhone,
                street: profile.street,
                postcode: profile.postcode,
                city: profile.city,
                emergencyName: profile.emergencyName ?? '',
                emergencyRelationship: profile.emergencyRelationship ?? '',
                emergencyPhone: profile.emergencyPhone ?? '',
              })
              setErrors({})
            }
          }}
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
    </div>
  )
}
