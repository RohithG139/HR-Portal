/**
 * Barrel export for shared UI components.
 * Every shared component is exported here so feature screens import from a single path.
 */

export { Badge, stageLabel, stageVariant } from './Badge/Badge'
export type { BadgeVariant } from './Badge/Badge'

export { Card } from './Card/Card'

export { Field } from './Field/Field'

export { Modal } from './Modal/Modal'

export { ToastProvider, useToast } from './Toast/ToastContext'

export { Table } from './Table/Table'
export type { Column } from './Table/Table'

export { Checklist } from './Checklist/Checklist'

export { TaskList } from './TaskList/TaskList'
